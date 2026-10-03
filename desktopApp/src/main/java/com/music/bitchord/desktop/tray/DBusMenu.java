package com.music.bitchord.desktop.tray;

import java.util.List;
import org.freedesktop.dbus.annotations.DBusInterfaceName;
import org.freedesktop.dbus.interfaces.DBusInterface;
import org.freedesktop.dbus.messages.DBusSignal;
import org.freedesktop.dbus.exceptions.DBusException;
import org.freedesktop.dbus.types.UInt32;
import org.freedesktop.dbus.types.Variant;

/** The menu behind a StatusNotifierItem, drawn by the shell. */
@DBusInterfaceName("com.canonical.dbusmenu")
public interface DBusMenu extends DBusInterface {

    DBusMenuLayout<UInt32, DBusMenuItem> GetLayout(int _parentId, int _recursionDepth, String[] _propertyNames);

    List<DBusMenuProperties> GetGroupProperties(int[] _ids, String[] _propertyNames);

    Variant<?> GetProperty(int _id, String _name);

    void Event(int _id, String _eventId, Variant<?> _data, UInt32 _timestamp);

    List<Integer> EventGroup(List<DBusMenuEvent> _events);

    boolean AboutToShow(int _id);

    DBusMenuShowGroup<int[], int[]> AboutToShowGroup(int[] _ids);

    /** Tells the host the menu it holds is stale and worth fetching again. */
    class LayoutUpdated extends DBusSignal {
        private final UInt32 revision;
        private final int    parent;

        public LayoutUpdated(String _path, UInt32 _revision, int _parent) throws DBusException {
            super(_path, _revision, _parent);
            revision = _revision;
            parent = _parent;
        }

        public UInt32 getRevision() {
            return revision;
        }

        public int getParent() {
            return parent;
        }
    }
}
