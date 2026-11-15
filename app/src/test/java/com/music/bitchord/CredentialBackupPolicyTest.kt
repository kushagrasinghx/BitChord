package com.music.bitchord

import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

class CredentialBackupPolicyTest {

    private fun rules(path: String): String = File("src/main/res/xml/$path").readText()

    @Test
    fun `addon token and tester credentials are excluded from backups`() {
        val backup = rules("backup_rules.xml")
        listOf(
            "bitchord_tester.xml",
            "bitchord_tester_plain.xml",
            "bitchord_addon_auth.xml",
            "bitchord_addon_auth_plain.xml",
            "bitchord_settings.xml",
            "bitchord_listen_together.xml",
            "bitchord_party_secret.xml",
            "bitchord_party_secret_plain.xml",
        ).forEach { name -> assertTrue("$name missing from backup_rules", backup.contains(name)) }
        assertTrue("listening history is missing from backup_rules", backup.contains("path=\"listening\""))
    }

    @Test
    fun `legacy and current extraction rules exclude addon credentials`() {
        val extraction = rules("data_extraction_rules.xml")
        listOf(
            "bitchord_tester.xml",
            "bitchord_tester_plain.xml",
            "bitchord_addon_auth.xml",
            "bitchord_addon_auth_plain.xml",
            "bitchord_settings.xml",
            "bitchord_listen_together.xml",
            "bitchord_party_secret.xml",
            "bitchord_party_secret_plain.xml",
        ).forEach { name -> assertTrue("$name missing from data_extraction_rules", extraction.contains(name)) }
    }
}
