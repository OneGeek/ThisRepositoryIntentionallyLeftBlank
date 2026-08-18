import java.util.Properties

plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
    id("org.jetbrains.kotlin.plugin.compose")
    id("com.google.devtools.ksp")
    id("io.github.takahirom.roborazzi")
}

// Release signing is driven by a git-ignored keystore.properties at the project
// root (see keystore.properties.template). When it's absent — e.g. a clean
// checkout or CI without secrets — assembleRelease still succeeds and produces
// the usual *-unsigned APK, so the build never depends on local secrets.
val keystorePropertiesFile = rootProject.file("keystore.properties")
val keystoreProperties = Properties().apply {
    if (keystorePropertiesFile.exists()) keystorePropertiesFile.inputStream().use { load(it) }
}
val hasReleaseSigning = keystorePropertiesFile.exists()

android {
    namespace = "com.tamawatch"
    compileSdk = 34

    defaultConfig {
        applicationId = "com.tamawatch"
        minSdk = 33            // Wear OS 4+ (Galaxy Watch Ultra runs Wear OS 5 / API 34)
        targetSdk = 34
        versionCode = 1
        versionName = "1.0"
        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
        vectorDrawables { useSupportLibrary = true }
    }

    signingConfigs {
        if (hasReleaseSigning) {
            create("release") {
                storeFile = rootProject.file(keystoreProperties.getProperty("storeFile"))
                storePassword = keystoreProperties.getProperty("storePassword")
                keyAlias = keystoreProperties.getProperty("keyAlias")
                keyPassword = keystoreProperties.getProperty("keyPassword")
            }
        }
    }

    buildTypes {
        release {
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
            if (hasReleaseSigning) signingConfig = signingConfigs.getByName("release")
        }
        debug {
            applicationIdSuffix = ".debug"
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    kotlinOptions { jvmTarget = "17" }

    buildFeatures { compose = true }

    // Keep generated pixel art un-scaled across densities.
    androidResources { noCompress += listOf("json") }

    packaging {
        resources { excludes += "/META-INF/{AL2.0,LGPL2.1}" }
    }

    ksp { arg("room.schemaLocation", "$projectDir/schemas") }

    // Robolectric-backed screenshot tests need real merged resources/assets.
    testOptions {
        unitTests {
            isIncludeAndroidResources = true
            isReturnDefaultValues = true
        }
    }
}

// Robolectric fetches its android-all runtime jar at test time, and its parallel
// downloader trips Maven Central's rate limiter (HTTP 429) through the agent proxy.
// Instead, let Gradle resolve that jar once (it retries + caches), stage it, and run
// Robolectric fully offline against the staged copy — no runtime download at all.
// Two configs, not one: android-all-instrumented 14 and 15 are the same Gradle
// module, so a single config would collapse to the highest version. Robolectric
// may ask for either SDK, so stage both.
val robolectricSdk34: Configuration by configurations.creating
val robolectricSdk35: Configuration by configurations.creating

// Stage the resolved android-all jars into a stable dir (a Sync task so this is
// configuration-cache friendly — the Test task only sees the path string).
val robolectricDepsDir = layout.buildDirectory.dir("robolectric-deps")
val stageRobolectricSdk = tasks.register<Sync>("stageRobolectricSdk") {
    from(robolectricSdk34)
    from(robolectricSdk35)
    into(robolectricDepsDir)
}
val robolectricDepsPath = robolectricDepsDir.get().asFile.absolutePath

tasks.withType<Test>().configureEach {
    dependsOn(stageRobolectricSdk)
    systemProperty("robolectric.offline", "true")
    systemProperty("robolectric.dependency.dir", robolectricDepsPath)
}

dependencies {
    val composeBom = platform("androidx.compose:compose-bom:2024.09.03")
    implementation(composeBom)
    androidTestImplementation(composeBom)

    // Compose runtime/graphics (Canvas, Image, animation)
    implementation("androidx.compose.ui:ui")
    implementation("androidx.compose.ui:ui-graphics")
    implementation("androidx.compose.foundation:foundation")
    implementation("androidx.compose.animation:animation")
    implementation("androidx.compose.ui:ui-tooling-preview")
    debugImplementation("androidx.compose.ui:ui-tooling")

    // Wear Compose
    implementation("androidx.wear.compose:compose-material:1.4.0")
    implementation("androidx.wear.compose:compose-foundation:1.4.0")
    implementation("androidx.wear.compose:compose-navigation:1.4.0")

    // Wear platform (ambient, input)
    implementation("androidx.wear:wear:1.3.0")
    implementation("androidx.wear:wear-input:1.1.0")

    // Activity / lifecycle
    implementation("androidx.core:core-ktx:1.13.1")
    implementation("androidx.activity:activity-compose:1.9.2")
    // Force the transitively-pulled Fragment (1.2.4, via wear) up to a current version.
    // The app uses ComponentActivity, not FragmentActivity, but lint's
    // InvalidFragmentVersionForActivityResult check fails the release build on any
    // Fragment < 1.3.0 present on the classpath; 1.8.x resolves it at the source.
    implementation("androidx.fragment:fragment:1.8.5")
    implementation("androidx.lifecycle:lifecycle-runtime-ktx:2.8.6")
    implementation("androidx.lifecycle:lifecycle-viewmodel-compose:2.8.6")
    implementation("androidx.lifecycle:lifecycle-runtime-compose:2.8.6")

    // Persistence
    implementation("androidx.room:room-runtime:2.6.1")
    implementation("androidx.room:room-ktx:2.6.1")
    ksp("androidx.room:room-compiler:2.6.1")
    implementation("androidx.datastore:datastore-preferences:1.1.1")

    // Background work
    implementation("androidx.work:work-runtime-ktx:2.9.1")

    // Tiles + Complications
    implementation("androidx.wear.tiles:tiles:1.4.1")
    implementation("androidx.wear.tiles:tiles-material:1.4.1")
    implementation("androidx.wear.protolayout:protolayout:1.2.1")
    implementation("androidx.wear.protolayout:protolayout-material:1.2.1")
    implementation("androidx.wear.protolayout:protolayout-expression:1.2.1")
    implementation("androidx.wear.watchface:watchface-complications-data-source-ktx:1.2.1")
    implementation("com.google.guava:guava:33.0.0-android")

    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.8.1")

    // Unit tests (pure engine — no device needed)
    testImplementation("junit:junit:4.13.2")
    testImplementation("org.jetbrains.kotlinx:kotlinx-coroutines-test:1.8.1")

    // JVM screenshot tests: render the real Compose UI to PNG via Robolectric
    // (NATIVE graphics) + Roborazzi — no emulator, no KVM.
    testImplementation(composeBom)
    testImplementation("androidx.compose.ui:ui-test-junit4")
    // Must be debug (not test) so its ComponentActivity merges into the debug
    // manifest that Robolectric tests against, else the compose rule can't launch.
    debugImplementation("androidx.compose.ui:ui-test-manifest")
    testImplementation("org.robolectric:robolectric:4.14.1")
    testImplementation("androidx.test.ext:junit:1.2.1")
    testImplementation("androidx.test:core-ktx:1.6.1")
    testImplementation("io.github.takahirom.roborazzi:roborazzi:1.32.0")
    testImplementation("io.github.takahirom.roborazzi:roborazzi-compose:1.32.0")
    testImplementation("io.github.takahirom.roborazzi:roborazzi-junit-rule:1.32.0")

    // Robolectric's SDK runtime jar, resolved by Gradle so it's cached and staged
    // for offline use (see the robolectricSdk config + Test task wiring above).
    robolectricSdk34("org.robolectric:android-all-instrumented:14-robolectric-10818077-i7")
    robolectricSdk35("org.robolectric:android-all-instrumented:15-robolectric-12650502-i7")
}
