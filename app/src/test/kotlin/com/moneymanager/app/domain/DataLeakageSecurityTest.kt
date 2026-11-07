package com.moneymanager.app.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File
import java.io.FileOutputStream

class DataLeakageSecurityTest {

    private fun findProjectRoot(): File {
        var current: File? = File(".").canonicalFile
        while (current != null) {
            if (File(current, "app/src/main/AndroidManifest.xml").exists()) {
                return current
            }
            current = current.parentFile
        }
        return File(".").canonicalFile
    }

    @Test
    fun testAndroidManifest_disablesAllowBackupAndConfiguresRules() {
        val root = findProjectRoot()
        val manifestFile = File(root, "app/src/main/AndroidManifest.xml")
        assertTrue("AndroidManifest.xml must exist at ${manifestFile.absolutePath}", manifestFile.exists())

        val content = manifestFile.readText()

        // 1. android:allowBackup must be explicitly false
        assertTrue(
            "AndroidManifest.xml must set android:allowBackup=\"false\" to prevent ADB/cloud data extraction",
            content.contains("android:allowBackup=\"false\"")
        )
        assertFalse(
            "AndroidManifest.xml must not set android:allowBackup=\"true\"",
            content.contains("android:allowBackup=\"true\"")
        )

        // 2. Backup and data extraction rules must be explicitly linked
        assertTrue(
            "AndroidManifest.xml must link fullBackupContent",
            content.contains("android:fullBackupContent=\"@xml/backup_rules\"")
        )
        assertTrue(
            "AndroidManifest.xml must link dataExtractionRules",
            content.contains("android:dataExtractionRules=\"@xml/data_extraction_rules\"")
        )
    }

    @Test
    fun testBackupRulesXml_activelyExcludesSensitiveDomains() {
        val root = findProjectRoot()
        val backupRulesFile = File(root, "app/src/main/res/xml/backup_rules.xml")
        assertTrue("backup_rules.xml must exist", backupRulesFile.exists())

        val xml = backupRulesFile.readText()
        // Strip comments to verify active rules
        val uncommented = xml.replace(Regex("<!--[\\s\\S]*?-->"), "")

        assertTrue(
            "backup_rules.xml must actively exclude database domain",
            uncommented.contains("<exclude domain=\"database\" path=\".\"/>")
        )
        assertTrue(
            "backup_rules.xml must actively exclude sharedpref domain",
            uncommented.contains("<exclude domain=\"sharedpref\" path=\".\"/>")
        )
    }

    @Test
    fun testDataExtractionRulesXml_activelyExcludesSensitiveDomainsInCloudAndDeviceTransfer() {
        val root = findProjectRoot()
        val dataRulesFile = File(root, "app/src/main/res/xml/data_extraction_rules.xml")
        assertTrue("data_extraction_rules.xml must exist", dataRulesFile.exists())

        val xml = dataRulesFile.readText()
        val uncommented = xml.replace(Regex("<!--[\\s\\S]*?-->"), "")

        assertTrue("data_extraction_rules.xml must contain <cloud-backup>", uncommented.contains("<cloud-backup>"))
        assertTrue("data_extraction_rules.xml must contain <device-transfer>", uncommented.contains("<device-transfer>"))

        // Count database and sharedpref excludes (should be in both cloud-backup and device-transfer)
        val dbExcludes = Regex("<exclude\\s+domain=\"database\"").findAll(uncommented).count()
        val prefExcludes = Regex("<exclude\\s+domain=\"sharedpref\"").findAll(uncommented).count()

        assertTrue("Database domain must be excluded from both cloud-backup and device-transfer", dbExcludes >= 2)
        assertTrue("SharedPreferences domain must be excluded from both cloud-backup and device-transfer", prefExcludes >= 2)
    }

    @Test
    fun testMainActivity_setsFlagSecureToPreventScreenCaptureLeakage() {
        val root = findProjectRoot()
        val activityFile = File(root, "app/src/main/kotlin/com/moneymanager/app/MainActivity.kt")
        assertTrue("MainActivity.kt must exist", activityFile.exists())

        val code = activityFile.readText()

        assertTrue(
            "MainActivity.kt must set WindowManager.LayoutParams.FLAG_SECURE to prevent task switcher / screenshot data leakage",
            code.contains("FLAG_SECURE")
        )
    }

    @Test
    fun testGitignore_excludesFinancialDatabaseAndBackupFiles() {
        val root = findProjectRoot()
        val gitignoreFile = File(root, ".gitignore")
        assertTrue(".gitignore must exist", gitignoreFile.exists())

        val lines = gitignoreFile.readLines().map { it.trim() }

        assertTrue(".gitignore must ignore *.mmbak backup files", lines.contains("*.mmbak"))
        assertTrue(".gitignore must ignore *.sqlite backup files", lines.contains("*.sqlite"))
        assertTrue(".gitignore must ignore *.sqlite3 backup files", lines.contains("*.sqlite3"))
        assertTrue(".gitignore must ignore *.db database files", lines.contains("*.db"))
        assertTrue(".gitignore must ignore *.db-wal write-ahead logs", lines.contains("*.db-wal"))
        assertTrue(".gitignore must ignore *.db-shm shared memory files", lines.contains("*.db-shm"))
    }

    @Test
    fun testSqliteBackupManager_headerValidationRejectsInvalidFiles() {
        val tempValid = File.createTempFile("test_valid_header", ".sqlite")
        val tempInvalid = File.createTempFile("test_invalid_header", ".sqlite")
        val tempSmall = File.createTempFile("test_small", ".sqlite")

        try {
            // Valid SQLite 3 Header
            FileOutputStream(tempValid).use { out ->
                out.write("SQLite format 3\u0000".toByteArray(Charsets.US_ASCII))
                out.write(ByteArray(100))
            }
            assertTrue(
                "Valid SQLite file with magic header must pass validation",
                SqliteBackupManager.isValidSqliteFile(tempValid)
            )

            // Invalid Header (e.g. text/html/malicious content)
            FileOutputStream(tempInvalid).use { out ->
                out.write("NOT_A_SQLITE_FILE_CONTENT".toByteArray(Charsets.UTF_8))
            }
            assertFalse(
                "File without SQLite format 3 magic header must be rejected",
                SqliteBackupManager.isValidSqliteFile(tempInvalid)
            )

            // Truncated / empty file (< 16 bytes)
            FileOutputStream(tempSmall).use { out ->
                out.write("short".toByteArray(Charsets.UTF_8))
            }
            assertFalse(
                "File smaller than 16 bytes must be rejected",
                SqliteBackupManager.isValidSqliteFile(tempSmall)
            )
        } finally {
            tempValid.delete()
            tempInvalid.delete()
            tempSmall.delete()
        }
    }

    @Test
    fun testReleaseBuildConfiguration_enablesR8MinifyShrinkResourcesAndCodeSigning() {
        val root = findProjectRoot()
        val buildGradle = File(root, "app/build.gradle.kts")
        assertTrue("app/build.gradle.kts must exist", buildGradle.exists())

        val content = buildGradle.readText()
        assertTrue("Release build must enable R8 minification (isMinifyEnabled = true)", content.contains("isMinifyEnabled = true"))
        assertTrue("Release build must enable resource shrinking (isShrinkResources = true)", content.contains("isShrinkResources = true"))
        assertTrue("Release build must configure signingConfigs", content.contains("signingConfigs"))
        assertTrue("Release build must attach release signingConfig", content.contains("signingConfigs.getByName(\"release\")"))
    }

    @Test
    fun testProguardRules_existsAndProtectsRoomEntitiesAndEnums() {
        val root = findProjectRoot()
        val proguardFile = File(root, "app/proguard-rules.pro")
        assertTrue("app/proguard-rules.pro must exist", proguardFile.exists())

        val rules = proguardFile.readText()
        assertTrue("ProGuard rules must keep Room entities", rules.contains("com.moneymanager.app.data.local.entity.**"))
        assertTrue("ProGuard rules must keep enum values/valueOf", rules.contains("-keepclassmembers enum *"))
    }

    @Test
    fun testPermissionManifest_enforcesOfflinePrivacyAndRegistersQuickAddNotification() {
        val root = findProjectRoot()
        val manifestFile = File(root, "app/src/main/AndroidManifest.xml")
        val content = manifestFile.readText()

        assertTrue("Manifest must declare POST_NOTIFICATIONS", content.contains("android.permission.POST_NOTIFICATIONS"))
        assertTrue("Manifest must declare RECEIVE_BOOT_COMPLETED", content.contains("android.permission.RECEIVE_BOOT_COMPLETED"))
        assertTrue("Manifest must declare INTERNET for direct Google API sync", content.contains("android.permission.INTERNET") && !content.contains("<uses-permission android:name=\"android.permission.INTERNET\" tools:node=\"remove\""))
        assertTrue("Manifest must declare ACCESS_NETWORK_STATE for WorkManager constraints", content.contains("android.permission.ACCESS_NETWORK_STATE"))
        assertTrue("Manifest must strip legacy storage permissions", content.contains("android.permission.READ_EXTERNAL_STORAGE") && content.contains("tools:node=\"remove\""))
        assertTrue("Manifest must disable cleartext traffic", content.contains("android:usesCleartextTraffic=\"false\""))
        assertTrue("Manifest must register QuickAddNotificationReceiver", content.contains(".notifications.QuickAddNotificationReceiver"))
        assertTrue("Manifest must register oauth2redirect intent-filter", content.contains("android:scheme=\"com.moneymanager.app\"") && content.contains("android:host=\"oauth2redirect\""))
    }

    @Test
    fun testAppIcon_replacesDefaultTemplateWithCustomAdaptiveAndMonochromeIcons() {
        val root = findProjectRoot()
        val bgFile = File(root, "app/src/main/res/drawable/ic_launcher_background.xml")
        val fgFile = File(root, "app/src/main/res/drawable/ic_launcher_foreground.xml")
        val monoFile = File(root, "app/src/main/res/drawable/ic_launcher_monochrome.xml")

        assertTrue("ic_launcher_background.xml must exist", bgFile.exists())
        assertTrue("ic_launcher_foreground.xml must exist", fgFile.exists())
        assertTrue("ic_launcher_monochrome.xml must exist", monoFile.exists())

        assertFalse(
            "ic_launcher_background.xml must not use default template green (#3DDC84)",
            bgFile.readText().contains("#3DDC84", ignoreCase = true)
        )
        assertTrue(
            "ic_launcher_foreground.xml must use CoralAccent (#FF6353)",
            fgFile.readText().contains("#FF6353", ignoreCase = true)
        )
    }

    @Test
    fun testQuickAddNotificationManager_definesPersistentQuickAddNotification() {
        val root = findProjectRoot()
        val managerFile = File(root, "app/src/main/kotlin/com/moneymanager/app/notifications/QuickAddNotificationManager.kt")
        assertTrue("QuickAddNotificationManager.kt must exist", managerFile.exists())

        val code = managerFile.readText()
        assertTrue("Notification must be ongoing", code.contains(".setOngoing(true)"))
        assertTrue("Notification must not auto-cancel", code.contains(".setAutoCancel(false)"))
        assertTrue("Notification must include Add Expense action", code.contains("\"Add Expense\""))
        assertTrue("Notification must include Add Income action", code.contains("\"Add Income\""))
        assertTrue("Notification must include Transfer action", code.contains("\"Transfer\""))

        val actionCount = Regex("\\.addAction\\(").findAll(code).count()
        org.junit.Assert.assertEquals("Notification must have exactly 3 action buttons", 3, actionCount)
    }

    @Test
    fun testProductionCode_doesNotContainHardcodedPersonalDeveloperPII() {
        val root = findProjectRoot()
        val mainSrcDir = File(root, "app/src/main/kotlin")
        assertTrue("Main source directory must exist", mainSrcDir.exists())

        val sensitivePatterns = listOf(
            "PRITESH RANJAN",
            "526873XXXXXX3453",
            "4315XXXXXXXX3004"
        )

        val violations = mutableListOf<String>()
        mainSrcDir.walkTopDown().filter { it.extension == "kt" }.forEach { file ->
            val content = file.readText()
            for (pattern in sensitivePatterns) {
                if (content.contains(pattern, ignoreCase = true)) {
                    violations.add("${file.relativeTo(root).path} contains prohibited developer literal: $pattern")
                }
            }
        }

        assertTrue(
            "Production source code must not contain hardcoded developer personal names or card numbers:\n${violations.joinToString("\n")}",
            violations.isEmpty()
        )
    }

    @Test
    fun testStatementEntityMapper_defensivelyMasksRawCardNumbers() {
        val rawPan16 = "4186881234568158"
        val masked = com.moneymanager.app.domain.statement.core.StatementEntityMapper.sanitizeCardMask(rawPan16)
        assertEquals("4186XXXXXXXX8158", masked)

        val alreadyMasked = "418688******8158"
        assertEquals(alreadyMasked, com.moneymanager.app.domain.statement.core.StatementEntityMapper.sanitizeCardMask(alreadyMasked))
    }

    @Test
    fun testGmailOAuthManager_escapesErrorParamInHtmlResponse() {
        val manager = com.moneymanager.app.domain.gmail.auth.GmailOAuthManager()
        val maliciousPayload = "<script>alert('xss')</script>"
        val html = manager.buildResponseHtml(isSuccess = false, error = maliciousPayload)

        assertFalse("HTML response must not contain unescaped error script tag", html.contains("<script>alert("))
        assertTrue("HTML response must contain escaped script entity", html.contains("&lt;script&gt;alert("))
    }
}
