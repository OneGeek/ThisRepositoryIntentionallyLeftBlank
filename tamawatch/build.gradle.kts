plugins {
    id("com.android.application") version "8.7.3" apply false
    id("org.jetbrains.kotlin.android") version "2.0.21" apply false
    id("org.jetbrains.kotlin.plugin.compose") version "2.0.21" apply false
    id("com.google.devtools.ksp") version "2.0.21-1.0.28" apply false
    // JVM screenshot testing: renders the real composables to PNG via Robolectric,
    // no emulator/KVM needed. See app/src/test/.../*SnapshotTest.kt.
    id("io.github.takahirom.roborazzi") version "1.32.0" apply false
}
