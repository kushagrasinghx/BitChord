package com.music.bitchord.desktop.tray;

import org.freedesktop.dbus.Struct;
import org.freedesktop.dbus.annotations.Position;
import org.freedesktop.dbus.types.UInt32;
import org.freedesktop.dbus.types.Variant;

/** A batched event: {@code (isvu)} — item, event name, payload, timestamp. */
public final class DBusMenuEvent extends Struct {

    @Position(0)
    private final int id;

    @Position(1)
    private final String eventId;

    @Position(2)
    private final Variant<?> data;

    @Position(3)
    private final UInt32 timestamp;

    public DBusMenuEvent(int _id, String _eventId, Variant<?> _data, UInt32 _timestamp) {
        id = _id;
        eventId = _eventId;
        data = _data;
        timestamp = _timestamp;
    }

    public int getId() {
        return id;
    }

    public String getEventId() {
        return eventId;
    }

    public Variant<?> getData() {
        return data;
    }

    public UInt32 getTimestamp() {
        return timestamp;
    }
}
