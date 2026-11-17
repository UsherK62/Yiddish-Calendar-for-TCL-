plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
}

android {
    namespace = "org.yiddishcalendar"
    compileSdk = 36
    defaultConfig {
        applicationId = "com.yiddishcalendar.app"
        minSdk = 21 // LG Exalt LTE (VN220) runs Android 6.0.1 = API 23; 21 leaves margin. java.time via desugaring.
        targetSdk = 30 // Android 11 phone; old-style runtime permissions
        vectorDrawables.useSupportLibrary = true
        versionCode = 12
        versionName = "0.2.7"
    }
    buildTypes {
        release {
            // First test build: no R8, so nothing reflective gets stripped. Signed afterwards with apksigner.
            isMinifyEnabled = false
            isShrinkResources = false
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
            // Signing: add a NEW throwaway key here later (never /workspace/hebcal/keystore).
        }
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
        // java.time (LocalDate etc.) is only built into Android 8.0+; desugaring adds it for older phones.
        isCoreLibraryDesugaringEnabled = true
    }
    buildFeatures { buildConfig = true }
    testOptions {
        unitTests.isReturnDefaultValues = true
        // Robolectric screenshot tests (host only, render mockups of the real layouts).
        unitTests.isIncludeAndroidResources = true
        unitTests.all { it.systemProperty("ycal.shots", rootProject.file("screens/fixes").absolutePath) }
    }
    // Sideloaded test build for an Android 11 keypad phone, not Google Play.
    lint { disable += "ExpiredTargetSdkVersion" }
}

kotlin { jvmToolchain(17) }

dependencies {
    coreLibraryDesugaring("com.android.tools:desugar_jdk_libs:2.1.5")
    testImplementation("junit:junit:4.13.2")
    testImplementation("org.robolectric:robolectric:4.14.1")
    testImplementation("androidx.test:core:1.6.1")
    implementation(project(":calendarlib"))
    implementation("com.kosherjava:zmanim:3.0.0")
    implementation("androidx.appcompat:appcompat:1.7.1")
    implementation("androidx.core:core-ktx:1.17.0")
}
