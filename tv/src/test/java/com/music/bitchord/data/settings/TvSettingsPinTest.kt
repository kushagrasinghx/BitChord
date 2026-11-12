package com.music.bitchord.data.settings

import android.content.SharedPreferences
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

class TvSettingsPinTest {

    private lateinit var fakePrefs: FakeSharedPreferences

    @Before
    fun setUp() {
        fakePrefs = FakeSharedPreferences()
        TvSettings.init(fakePrefs)
    }

    @Test
    fun testPinIsStoredAsSaltedPbkdf2HashNotPlaintext() {
        val profileId = "profile_1"
        val plainPin = "1234"

        TvSettings.setProfilePin(profileId, plainPin)

        val rawStored = fakePrefs.getString("tv_profile_pin_$profileId", null)
        assertTrue("Stored value should not be null", rawStored != null)
        // Ensure plaintext PIN is never stored directly
        assertFalse("Raw PIN must not appear in stored preference", rawStored!!.contains(plainPin))
        // Verify format pbkdf2_sha256:<iterations>:<saltHex>:<hashHex>
        assertTrue("Stored value must start with pbkdf2_sha256 prefix", rawStored.startsWith("pbkdf2_sha256:"))
        val parts = rawStored.split(':')
        assertEquals("Stored hash should have 4 colon-separated parts", 4, parts.size)
        assertEquals("Algorithm prefix", "pbkdf2_sha256", parts[0])
        assertEquals("Iterations", "120000", parts[1])
        assertEquals("Salt length in hex (16 bytes = 32 hex chars)", 32, parts[2].length)
        assertEquals("Hash length in hex (32 bytes = 64 hex chars)", 64, parts[3].length)
    }

    @Test
    fun testUniqueSaltsPerProfileAndCall() {
        val pin = "5678"
        TvSettings.setProfilePin("profile_a", pin)
        TvSettings.setProfilePin("profile_b", pin)

        val storedA = fakePrefs.getString("tv_profile_pin_profile_a", null)!!
        val storedB = fakePrefs.getString("tv_profile_pin_profile_b", null)!!

        // Salts must be unique, so hashes must differ even with the identical PIN
        assertNotEquals(
            "Different profiles must produce different salted hashes",
            storedA,
            storedB,
        )
    }

    @Test
    fun testVerifyProfilePin() {
        val profileId = "profile_verify"
        TvSettings.setProfilePin(profileId, "4321")

        assertTrue("Correct PIN should verify successfully", TvSettings.verifyProfilePin(profileId, "4321"))
        assertFalse("Wrong PIN should fail", TvSettings.verifyProfilePin(profileId, "0000"))
        assertFalse("Wrong PIN length should fail", TvSettings.verifyProfilePin(profileId, "432"))
        assertFalse("Blank PIN should fail", TvSettings.verifyProfilePin(profileId, ""))
        assertFalse("Unknown profile should fail", TvSettings.verifyProfilePin("non_existent", "4321"))
    }

    @Test
    fun testProfileLockStateAndClear() {
        val profileId = "profile_lock"
        assertFalse(TvSettings.isProfileLocked(profileId))

        TvSettings.setProfilePin(profileId, "9999")
        assertTrue(TvSettings.isProfileLocked(profileId))

        // Clearing with null
        TvSettings.setProfilePin(profileId, null)
        assertFalse(TvSettings.isProfileLocked(profileId))
        assertFalse(TvSettings.verifyProfilePin(profileId, "9999"))

        // Setting again and clearing with blank
        TvSettings.setProfilePin(profileId, "8888")
        assertTrue(TvSettings.isProfileLocked(profileId))
        TvSettings.setProfilePin(profileId, "   ")
        assertFalse(TvSettings.isProfileLocked(profileId))
    }

    @Test
    fun testWipeAllPins() {
        TvSettings.setProfilePin("p1", "1111")
        TvSettings.setProfilePin("p2", "2222")
        TvSettings.setProfilePin("p3", "3333")

        assertTrue(TvSettings.isProfileLocked("p1"))
        assertTrue(TvSettings.isProfileLocked("p2"))
        assertTrue(TvSettings.isProfileLocked("p3"))

        TvSettings.wipeAllPins()

        assertFalse(TvSettings.isProfileLocked("p1"))
        assertFalse(TvSettings.isProfileLocked("p2"))
        assertFalse(TvSettings.isProfileLocked("p3"))
    }

    @Test
    fun testLegacyPlaintextPinMigration() {
        val profileId = "legacy_profile"
        val plainPin = "1234"

        // Simulate legacy storage where PIN was stored as plaintext
        fakePrefs.edit().putString("tv_profile_pin_$profileId", plainPin).commit()
        assertEquals("1234", fakePrefs.getString("tv_profile_pin_$profileId", null))
        assertTrue(TvSettings.isProfileLocked(profileId))

        // Wrong PIN fails and leaves legacy storage intact
        assertFalse(TvSettings.verifyProfilePin(profileId, "9999"))
        assertEquals("1234", fakePrefs.getString("tv_profile_pin_$profileId", null))

        // Correct PIN succeeds and automatically upgrades to PBKDF2 salted hash
        assertTrue(TvSettings.verifyProfilePin(profileId, plainPin))

        val upgraded = fakePrefs.getString("tv_profile_pin_$profileId", null)!!
        assertTrue("Legacy PIN must be migrated to PBKDF2 hash", upgraded.startsWith("pbkdf2_sha256:"))
        assertFalse("Plaintext must no longer be present", upgraded.contains(plainPin))

        // Subsequent verification works with the newly upgraded hash
        assertTrue(TvSettings.verifyProfilePin(profileId, plainPin))
    }

    @Test
    fun testMalformedStoredHashFailsGracefully() {
        val profileId = "corrupt"
        fakePrefs.edit().putString("tv_profile_pin_$profileId", "pbkdf2_sha256:not_a_number:bad_salt:bad_hash").commit()
        assertFalse(TvSettings.verifyProfilePin(profileId, "1234"))

        fakePrefs.edit().putString("tv_profile_pin_$profileId", "pbkdf2_sha256:20000:invalid_hex:1234").commit()
        assertFalse(TvSettings.verifyProfilePin(profileId, "1234"))
    }

    @Test
    fun testOlderIterationCountVerifiesAndUpgradesOnSuccess() {
        val profileId = "profile_old_cost"
        val salt = ByteArray(16) { it.toByte() }
        val hash = javax.crypto.SecretKeyFactory.getInstance("PBKDF2WithHmacSHA256")
            .generateSecret(javax.crypto.spec.PBEKeySpec("2468".toCharArray(), salt, 20_000, 256)).encoded
        fun hex(b: ByteArray) = b.joinToString("") { "%02x".format(it) }
        fakePrefs.edit().putString("tv_profile_pin_$profileId", "pbkdf2_sha256:20000:${hex(salt)}:${hex(hash)}").apply()

        assertFalse(TvSettings.verifyProfilePin(profileId, "0000"))
        assertEquals("20000", fakePrefs.getString("tv_profile_pin_$profileId", null)!!.split(':')[1])

        assertTrue(TvSettings.verifyProfilePin(profileId, "2468"))
        assertEquals("120000", fakePrefs.getString("tv_profile_pin_$profileId", null)!!.split(':')[1])
        assertTrue(TvSettings.verifyProfilePin(profileId, "2468"))
    }
}

class FakeSharedPreferences : SharedPreferences {
    private val data = mutableMapOf<String, Any>()

    override fun getAll(): MutableMap<String, *> = HashMap(data)

    override fun getString(key: String?, defValue: String?): String? =
        data[key] as? String ?: defValue

    @Suppress("UNCHECKED_CAST")
    override fun getStringSet(key: String?, defValues: MutableSet<String>?): MutableSet<String>? =
        (data[key] as? Set<String>)?.toMutableSet() ?: defValues

    override fun getInt(key: String?, defValue: Int): Int =
        (data[key] as? Int) ?: defValue

    override fun getLong(key: String?, defValue: Long): Long =
        (data[key] as? Long) ?: defValue

    override fun getFloat(key: String?, defValue: Float): Float =
        (data[key] as? Float) ?: defValue

    override fun getBoolean(key: String?, defValue: Boolean): Boolean =
        (data[key] as? Boolean) ?: defValue

    override fun contains(key: String?): Boolean = data.containsKey(key)

    override fun edit(): SharedPreferences.Editor = EditorImpl()

    override fun registerOnSharedPreferenceChangeListener(listener: SharedPreferences.OnSharedPreferenceChangeListener?) {}

    override fun unregisterOnSharedPreferenceChangeListener(listener: SharedPreferences.OnSharedPreferenceChangeListener?) {}

    private inner class EditorImpl : SharedPreferences.Editor {
        private val pending = mutableMapOf<String, Any?>()
        private var clear = false

        override fun putString(key: String?, value: String?): SharedPreferences.Editor {
            if (key != null) pending[key] = value
            return this
        }

        override fun putStringSet(key: String?, values: MutableSet<String>?): SharedPreferences.Editor {
            if (key != null) pending[key] = values?.toSet()
            return this
        }

        override fun putInt(key: String?, value: Int): SharedPreferences.Editor {
            if (key != null) pending[key] = value
            return this
        }

        override fun putLong(key: String?, value: Long): SharedPreferences.Editor {
            if (key != null) pending[key] = value
            return this
        }

        override fun putFloat(key: String?, value: Float): SharedPreferences.Editor {
            if (key != null) pending[key] = value
            return this
        }

        override fun putBoolean(key: String?, value: Boolean): SharedPreferences.Editor {
            if (key != null) pending[key] = value
            return this
        }

        override fun remove(key: String?): SharedPreferences.Editor {
            if (key != null) pending[key] = null
            return this
        }

        override fun clear(): SharedPreferences.Editor {
            clear = true
            return this
        }

        override fun commit(): Boolean {
            apply()
            return true
        }

        override fun apply() {
            if (clear) {
                data.clear()
            }
            for ((key, value) in pending) {
                if (value == null) {
                    data.remove(key)
                } else {
                    data[key] = value
                }
            }
            pending.clear()
            clear = false
        }
    }
}
