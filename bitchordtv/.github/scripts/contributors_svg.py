"""Draws the README's contributor grid from the live GitHub contributor list.

contrib.rocks used to draw it, but it caches each repo's list on its own side
and stopped picking up new contributors. This matches its layout (12 columns of
64px circles, 4px apart) so the README looks the same, with avatars embedded as
base64 because an SVG shown through <img> may not load anything external.

Usage: GH_TOKEN=... REPO=owner/name python contributors_svg.py out.svg
"""

import base64
import json
import os
import sys
import urllib.request
from xml.sax.saxutils import escape

COLUMNS = 12
SIZE = 64
GAP = 4
# Fetched at 2x so the circles stay sharp on high-density screens.
AVATAR_PX = SIZE * 2
# Accounts that show up in the commit history but aren't people.
EXCLUDE = {"claude"}


def get(url, token):
    req = urllib.request.Request(url)
    if token:
        req.add_header("Authorization", f"Bearer {token}")
    with urllib.request.urlopen(req, timeout=30) as res:
        return res.read(), res.headers


def contributors(repo, token):
    users, page = [], 1
    while True:
        body, _ = get(f"https://api.github.com/repos/{repo}/contributors?per_page=100&page={page}", token)
        batch = json.loads(body)
        if not batch:
            return users
        for c in batch:
            login = c.get("login", "")
            if c.get("type") == "User" and not login.endswith("[bot]") and login.lower() not in EXCLUDE:
                users.append(c)
        page += 1


def avatar(c, token):
    url = c["avatar_url"] + ("&" if "?" in c["avatar_url"] else "?") + f"s={AVATAR_PX}"
    body, headers = get(url, token)
    mime = headers.get("Content-Type", "image/png").split(";")[0]
    return f"data:{mime};base64,{base64.b64encode(body).decode()}"


def render(users, token):
    rows = max(1, -(-len(users) // COLUMNS))
    width = COLUMNS * SIZE + (COLUMNS - 1) * GAP
    height = rows * SIZE + (rows - 1) * GAP
    r = SIZE // 2
    out = [
        f'<svg xmlns="http://www.w3.org/2000/svg" xmlns:xlink="http://www.w3.org/1999/xlink" '
        f'width="{width}" height="{height}" viewBox="0 0 {width} {height}">'
    ]
    for i, c in enumerate(users):
        x = (i % COLUMNS) * (SIZE + GAP)
        y = (i // COLUMNS) * (SIZE + GAP)
        login = escape(c["login"])
        out.append(
            f'<svg x="{x}" y="{y}" width="{SIZE}" height="{SIZE}"><title>{login}</title>'
            f'<defs><pattern id="a{i}" width="{SIZE}" height="{SIZE}" patternUnits="userSpaceOnUse">'
            f'<image width="{SIZE}" height="{SIZE}" xlink:href="{avatar(c, token)}"/></pattern></defs>'
            f'<circle cx="{r}" cy="{r}" r="{r - 0.5}" stroke="#c0c0c0" stroke-width="1" fill="url(#a{i})"/></svg>'
        )
    out.append("</svg>")
    return "\n".join(out) + "\n"


def main():
    token = os.environ.get("GH_TOKEN", "")
    users = contributors(os.environ["REPO"], token)
    svg = render(users, token)
    with open(sys.argv[1], "w", encoding="utf-8", newline="\n") as f:
        f.write(svg)
    print(f"{len(users)} contributors, {len(svg) // 1024} KiB")


if __name__ == "__main__":
    main()
