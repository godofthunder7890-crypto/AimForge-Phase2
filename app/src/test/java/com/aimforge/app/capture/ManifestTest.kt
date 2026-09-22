package com.aimforge.app.capture

import java.io.File
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** Guards the privacy promises: no network, no accessibility, real mediaProjection service. */
class ManifestTest {
    private val manifest: String by lazy { File("src/main/AndroidManifest.xml").readText() }

    @Test fun noInternetPermission() = assertFalse(manifest.contains("android.permission.INTERNET"))

    @Test fun noAccessibilityService() {
        assertFalse(manifest.contains("BIND_ACCESSIBILITY_SERVICE"))
        assertFalse(manifest.contains("AccessibilityService"))
    }

    @Test fun noStoragePermissions() {
        assertFalse(manifest.contains("READ_EXTERNAL_STORAGE"))
        assertFalse(manifest.contains("WRITE_EXTERNAL_STORAGE"))
        assertFalse(manifest.contains("MANAGE_EXTERNAL_STORAGE"))
    }

    @Test fun captureServiceIsAMediaProjectionForegroundService() {
        assertTrue(manifest.contains("android.permission.FOREGROUND_SERVICE_MEDIA_PROJECTION"))
        assertTrue(manifest.contains("android:foregroundServiceType=\"mediaProjection\""))
        assertTrue(manifest.contains(".capture.CaptureService"))
    }

    @Test fun captureServiceIsNotExported() {
        val idx = manifest.indexOf(".capture.CaptureService")
        assertTrue(manifest.substring(idx, manifest.indexOf("/>", idx)).contains("android:exported=\"false\""))
    }
}
