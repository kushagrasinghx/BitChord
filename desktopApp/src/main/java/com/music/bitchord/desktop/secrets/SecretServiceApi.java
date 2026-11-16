package com.music.bitchord.desktop.secrets;

import java.util.List;
import java.util.Map;

import org.freedesktop.dbus.DBusPath;
import org.freedesktop.dbus.Struct;
import org.freedesktop.dbus.Tuple;
import org.freedesktop.dbus.annotations.DBusInterfaceName;
import org.freedesktop.dbus.annotations.Position;
import org.freedesktop.dbus.interfaces.DBusInterface;
import org.freedesktop.dbus.types.Variant;

/** The freedesktop Secret Service, as much of it as reading and writing one password needs. */
public final class SecretServiceApi {

    private SecretServiceApi() {
    }

    /** The bus name and the object every call starts from. */
    public static final String BUS_NAME = "org.freedesktop.secrets";
    public static final String SERVICE_PATH = "/org/freedesktop/secrets";

    /**
     * A secret in transit: which session it is encoded for, the algorithm parameters, the bytes,
     * and what they are.
     */
    public static final class Secret extends Struct {
        @Position(0)
        public final DBusPath session;
        @Position(1)
        public final byte[] parameters;
        @Position(2)
        public final byte[] value;
        @Position(3)
        public final String contentType;

        public Secret(DBusPath session, byte[] parameters, byte[] value, String contentType) {
            this.session = session;
            this.parameters = parameters;
            this.value = value;
            this.contentType = contentType;
        }
    }

    /** {@code OpenSession}'s pair: the algorithm's output, and the session. */
    public static final class OpenSessionResult<A, B> extends Tuple {
        @Position(0)
        private final A first;
        @Position(1)
        private final B second;

        public OpenSessionResult(A _first, B _second) {
            first = _first;
            second = _second;
        }

        public A getOutput() {
            return first;
        }

        public B getSession() {
            return second;
        }
    }

    /** {@code SearchItems}'s pair: what is readable now, and what is locked. */
    public static final class SearchResult<A, B> extends Tuple {
        @Position(0)
        private final A first;
        @Position(1)
        private final B second;

        public SearchResult(A _first, B _second) {
            first = _first;
            second = _second;
        }

        public A getUnlocked() {
            return first;
        }

        public B getLocked() {
            return second;
        }
    }

    /** {@code Unlock}'s pair: what was unlocked, and a prompt if one is needed. */
    public static final class UnlockResult<A, B> extends Tuple {
        @Position(0)
        private final A first;
        @Position(1)
        private final B second;

        public UnlockResult(A _first, B _second) {
            first = _first;
            second = _second;
        }

        public A getUnlocked() {
            return first;
        }

        public B getPrompt() {
            return second;
        }
    }

    /** {@code CreateItem}'s pair: the item, and a prompt if one is needed. */
    public static final class CreateItemResult<A, B> extends Tuple {
        @Position(0)
        private final A first;
        @Position(1)
        private final B second;

        public CreateItemResult(A _first, B _second) {
            first = _first;
            second = _second;
        }

        public A getItem() {
            return first;
        }

        public B getPrompt() {
            return second;
        }
    }

    @DBusInterfaceName("org.freedesktop.Secret.Service")
    public interface Service extends DBusInterface {
        OpenSessionResult<Variant<?>, DBusPath> OpenSession(String algorithm, Variant<?> input);

        SearchResult<List<DBusPath>, List<DBusPath>> SearchItems(Map<String, String> attributes);

        UnlockResult<List<DBusPath>, DBusPath> Unlock(List<DBusPath> objects);

        Map<DBusPath, Secret> GetSecrets(List<DBusPath> items, DBusPath session);

        DBusPath ReadAlias(String name);
    }

    @DBusInterfaceName("org.freedesktop.Secret.Collection")
    public interface Collection extends DBusInterface {
        CreateItemResult<DBusPath, DBusPath> CreateItem(
                Map<String, Variant<?>> properties, Secret secret, boolean replace);
    }

    @DBusInterfaceName("org.freedesktop.Secret.Item")
    public interface Item extends DBusInterface {
        void Delete();
    }
}
