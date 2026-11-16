package com.music.bitchord.desktop.tray;

import org.freedesktop.dbus.Tuple;
import org.freedesktop.dbus.annotations.Position;

/** {@code GetLayout}'s pair of return values: a revision and the subtree. */
public final class DBusMenuLayout<A, B> extends Tuple {

    @Position(0)
    private final A revision;

    @Position(1)
    private final B layout;

    public DBusMenuLayout(A _revision, B _layout) {
        revision = _revision;
        layout = _layout;
    }

    public A getRevision() {
        return revision;
    }

    public B getLayout() {
        return layout;
    }
}
