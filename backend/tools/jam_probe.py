"""
Drives a real party over the wire, the way two phones would.

Written to answer one question that reading could not: which of the ways an
AutoPlay top-up can be refused actually happen against the running server. The
app sends `queueAdd` and never looks at the answer -- an `error` frame only
lands in a log line -- so every refusal here is a silent "AutoPlay did nothing"
on somebody's phone.

Usage: python jam_probe.py [server]   (default: the LISTEN_TOGETHER_SERVER in
local.properties). Creates real parties on it; the server sweeps them once
everybody disconnects.
"""

import asyncio
import json
import sys
import uuid

import websockets
from urllib.request import Request, urlopen

DEFAULT_SERVER = "https://bitchord-67v8.onrender.com"


def post(url, body):
    req = Request(
        url,
        data=json.dumps(body).encode(),
        headers={"Content-Type": "application/json"},
        method="POST",
    )
    with urlopen(req, timeout=30) as resp:
        return json.loads(resp.read())


def identity(name):
    return {
        "userId": f"probe-{uuid.uuid4().hex[:12]}",
        "deviceId": f"dev-{uuid.uuid4().hex[:12]}",
        "displayName": name,
    }


def track(n, from_autoplay=False):
    return {
        "videoId": f"vid{n:04d}",
        "title": f"Track {n}",
        "artist": "Probe",
        "durationMs": 180000,
        "fromAutoplay": from_autoplay,
    }


class Member:
    """One phone: its socket, and whatever the server has told it so far."""

    def __init__(self, code, token, you, base):
        self.code = code
        self.token = token
        self.you = you
        self.ws_url = base.replace("https://", "wss://").replace("http://", "ws://")
        self.ws = None
        self.state = None
        self.queue = None
        self.member_list = None
        self.errors = []

    async def connect(self):
        self.ws = await websockets.connect(
            f"{self.ws_url}/ws/parties/{self.code}",
            additional_headers={"Authorization": f"Bearer {self.token}"},
        )
        asyncio.create_task(self._read())
        await asyncio.sleep(0.6)

    async def _read(self):
        try:
            async for raw in self.ws:
                frame = json.loads(raw)
                kind = frame.get("type")
                if kind in ("welcome", "state"):
                    if frame.get("playback"):
                        self.state = frame["playback"]
                    elif frame.get("party"):
                        self.state = frame["party"].get("playback")
                if kind == "queue":
                    self.queue = frame.get("queue")
                if kind == "members":
                    self.member_list = frame.get("members")
                if kind == "welcome":
                    self.member_list = frame.get("party", {}).get("members")
                if kind == "error":
                    self.errors.append((frame.get("error"), frame.get("message")))
        except Exception:
            pass

    async def control(self, action, **fields):
        self.errors.clear()
        await self.ws.send(json.dumps({"type": "control", "action": action, **fields}))
        await asyncio.sleep(0.8)
        return list(self.errors)

    async def members(self):
        await asyncio.sleep(0.3)
        return self.member_list or []

    async def sync_queue(self):
        await self.ws.send(json.dumps({"type": "syncQueue"}))
        await asyncio.sleep(0.8)
        return self.queue

    async def close(self):
        if self.ws:
            await self.ws.close()


async def make_party(base, host_name="Host"):
    body = post(f"{base}/api/parties", identity(host_name))
    host = Member(body["code"], body["token"], body["you"], base)
    await host.connect()
    return host


async def join(base, code, name):
    body = post(f"{base}/api/parties/{code}/join", identity(name))
    guest = Member(body["code"], body["token"], body["you"], base)
    await guest.connect()
    return guest


def upcoming_of(queue, index):
    items = (queue or {}).get("items", [])
    if index is None or index < 0:
        return len(items)
    return max(len(items) - 1 - index, 0)


# ---------------------------------------------------------------- the probes --

async def probe_normal(base, report):
    """The happy path, so a failure below means something other than the setup."""
    host = await make_party(base)
    try:
        await host.control(
            "setQueue",
            queue=[track(i) for i in range(5)],
            queueIndex=0,
        )
        errs = await host.control("queueAdd", tracks=[track(100 + i, True) for i in range(10)])
        q = await host.sync_queue()
        report("top-up with room to spare", not errs and len(q["items"]) == 15,
               f"errors={errs} len={len(q['items'])}")
    finally:
        await host.close()


async def probe_queue_index_lost(base, report):
    """
    A queue whose current track is not in it -- what the app publishes whenever
    the playing item is a device file, since those are stripped from the list
    but the index is still looked up in the stripped copy.
    """
    host = await make_party(base)
    try:
        await host.control("setQueue", queue=[track(i) for i in range(20)], queueIndex=-1)
        q = await host.sync_queue()
        errs = await host.control("queueAdd", tracks=[track(200 + i, True) for i in range(10)])
        after = await host.sync_queue()
        report("top-up after queueIndex -1", not errs,
               f"errors={errs} before={len(q['items'])} after={len(after['items'])}")
    finally:
        await host.close()


async def probe_full_queue(base, report):
    """A long queue the listener chose: does AutoPlay get any of the slots?"""
    host = await make_party(base)
    try:
        await host.control("setQueue", queue=[track(i) for i in range(26)], queueIndex=0)
        errs = await host.control("queueAdd", tracks=[track(300 + i, True) for i in range(10)])
        q = await host.sync_queue()
        report("top-up against a full queue", not errs,
               f"errors={errs} len={len(q['items'])}")
    finally:
        await host.close()


async def probe_listener_supplier(base, report):
    """
    The host has gone but the party plays on: the next member by id becomes the
    AutoPlay supplier. In a host-only party, can it actually supply?
    """
    host = await make_party(base)
    guest = None
    try:
        guest = await join(base, host.code, "Listener")
        await host.control("setQueue", queue=[track(i) for i in range(3)], queueIndex=0)
        await host.control("setHostOnlyControl", enabled=True)
        # The host's phone drops off -- backgrounded, tunnel, screen off long
        # enough for the socket to go. It is still a member; it is just not
        # connected, which is what hands AutoPlay to the next member by id.
        await host.ws.close()
        await asyncio.sleep(1.5)
        members = await guest.members()
        host_connected = [m["connected"] for m in members if m["isHost"]]
        errs = await guest.control("queueAdd", tracks=[track(400 + i, True) for i in range(5)])
        report("listener supplies AutoPlay while the locked party's host is offline",
               not errs, f"errors={errs} hostConnected={host_connected}")
    finally:
        if guest:
            await guest.close()
        await host.close()


async def probe_autoplay_flag_on_join(base, report):
    """
    AutoPlay is party-wide. A joiner sends its own preference on the way in --
    does joining with it off turn it off for everybody?
    """
    body = post(f"{base}/api/parties", {**identity("Host"), "autoplayEnabled": True})
    host = Member(body["code"], body["token"], body["you"], base)
    await host.connect()
    guest = None
    try:
        before = (host.state or {}).get("autoplayEnabled")
        gbody = post(
            f"{base}/api/parties/{host.code}/join",
            {**identity("Listener"), "autoplayEnabled": False},
        )
        guest = Member(gbody["code"], gbody["token"], gbody["you"], base)
        await guest.connect()
        await host.ws.send(json.dumps({"type": "sync"}))
        await asyncio.sleep(0.8)
        after = (host.state or {}).get("autoplayEnabled")
        report("joining with AutoPlay off leaves the party's setting alone",
               before is True and after is True, f"before={before} after={after}")
    finally:
        if guest:
            await guest.close()
        await host.close()


async def main():
    base = (sys.argv[1] if len(sys.argv) > 1 else DEFAULT_SERVER).rstrip("/")
    print(f"probing {base}\n")
    results = []

    def report(name, ok, detail):
        results.append((name, ok, detail))
        print(f"  [{'PASS' if ok else 'FAIL'}] {name}\n         {detail}")

    for probe in (
        probe_normal,
        probe_queue_index_lost,
        probe_full_queue,
        probe_listener_supplier,
        probe_autoplay_flag_on_join,
    ):
        try:
            await probe(base, report)
        except Exception as exc:  # a probe that cannot run says so rather than hiding
            report(probe.__name__, False, f"probe itself failed: {exc!r}")
        # The server allows two party creations a minute per IP.
        await asyncio.sleep(31)

    print("\n--- summary ---")
    for name, ok, detail in results:
        print(f"{'PASS' if ok else 'FAIL'}  {name}")


asyncio.run(main())
