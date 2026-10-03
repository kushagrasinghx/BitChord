package com.music.bitchord.desktop.tray;

import java.util.Map;
import org.freedesktop.dbus.Struct;
import org.freedesktop.dbus.annotations.Position;
import org.freedesktop.dbus.types.Variant;

/** One item's properties without its children: {@code (ia{sv})}. */
public final class DBusMenuProperties extends Struct {

    @Position(0)
    private final int id;

    @Position(1)
    private final Map<String, Variant<?>> properties;

    public DBusMenuProperties(int _id, Map<String, Variant<?>> _properties) {
        id = _id;
        properties = _properties;
    }

    public int getId() {
        return id;
    }

    public Map<String, Variant<?>> getProperties() {
        return properties;
    }
}
