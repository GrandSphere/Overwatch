package com.grandsphere.overwatch.runtime

/**
 * GitHub sideload: keep false so the APK does not advertise Accessibility to Play Protect.
 * After a Play listing: set true and uncomment the OverwatchAccessibilityService
 * service block in AndroidManifest.xml.
 */
object AccessibilityFeatures {
    const val ADVERTISED = false
}
