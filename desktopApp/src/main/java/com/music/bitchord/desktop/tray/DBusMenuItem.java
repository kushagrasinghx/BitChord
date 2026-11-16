package com.music.bitchord.desktop.tray;

import java.util.List;
import java.util.Map;
import org.freedesktop.dbus.Struct;
import org.freedesktop.dbus.annotations.Position;
import org.freedesktop.dbus.types.Variant;

/** One node of a dbusmenu layout: {@code (ia{sv}av)}. */
public final class DBusMenuItem extends Struct {

    @Position(0)
    private final int id;

    @Position(1)
    private final Map<String, Variant<?>> properties;

    @Position(2)
    private final List<Variant<?>> children;

    public DBusMenuItem(int _id, Map<String, Variant<?>> _properties, List<Variant<?>> _children) {
        id = _id;
        properties = _properties;
        children = _children;
    }

    public int getId() {
        return id;
    }

    public Map<String, Variant<?>> getProperties() {
        return properties;
    }

    public List<Variant<?>> getChildren() {
        return children;
    }
}
