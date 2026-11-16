package com.music.bitchord.desktop.tray;

import org.freedesktop.dbus.Tuple;
import org.freedesktop.dbus.annotations.Position;

/** {@code AboutToShowGroup}'s two lists: items needing an update, and failures. */
public final class DBusMenuShowGroup<A, B> extends Tuple {

    @Position(0)
    private final A updatesNeeded;

    @Position(1)
    private final B idErrors;

    public DBusMenuShowGroup(A _updatesNeeded, B _idErrors) {
        updatesNeeded = _updatesNeeded;
        idErrors = _idErrors;
    }

    public A getUpdatesNeeded() {
        return updatesNeeded;
    }

    public B getIdErrors() {
        return idErrors;
    }
}
