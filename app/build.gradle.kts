plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
}

android {
    namespace = "org.yiddishcalendar"
    compileSdk = 36
    defaultConfig {
        applicationId = "com.yiddishcalendar.app"
        minSdk = 26
        targetSdk = 30 // Android 11 phone; old-style runtime permissions
        versionCode = 6
        versionName = "0.2.1"
    }
    signingConfigs {
        // Optional release signing from a gitignored keystore.properties file
        // or environment variables. If those are not set, release falls back
        // to the debug key (see buildTypes.release).
        val props = java.util.Properties()
        val propsFile = rootProject.file("keystore.properties")
        if (propsFile.isFile) {
            propsFile.inputStream().use { props.load(it) }
        }
        fun cfg(prop: String, env: String): String? =
            System.getenv(env)?.takeIf { it.isNotBlank() }
                ?: props.getProperty(prop)?.takeIf { it.isNotBlank() }

        val storePath = cfg("storeFile", "YIDDISH_CALENDAR_STORE_FILE")
        val storePasswordValue = cfg("storePassword", "YIDDISH_CALENDAR_STORE_PASSWORD")
        val keyAliasValue = cfg("keyAlias", "YIDDISH_CALENDAR_KEY_ALIAS")
        val keyPasswordValue = cfg("keyPassword", "YIDDISH_CALENDAR_KEY_PASSWORD")
        val store = storePath?.let { path ->
            val asIs = file(path)
            when {
                asIs.isFile -> asIs
                rootProject.file(path).isFile -> rootProject.file(path)
                else -> null
            }
        }
        if (store != null &&
            storePasswordValue != null &&
            keyAliasValue != null &&
            keyPasswordValue != null
        ) {
            create("release") {
                storeFile = store
                storePassword = storePasswordValue
                keyAlias = keyAliasValue
                keyPassword = keyPasswordValue
            }
        }
    }
    buildTypes {
        release {
            // First test builds: no R8, so nothing reflective gets stripped.
            isMinifyEnabled = false
            isShrinkResources = false
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
            signingConfig = signingConfigs.findByName("release")
                ?: signingConfigs.getByName("debug")
        }
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    buildFeatures { buildConfig = true }
    testOptions { unitTests.isReturnDefaultValues = true }
    // Sideloaded test build for an Android 11 keypad phone, not Google Play.
    lint { disable += "ExpiredTargetSdkVersion" }
}

kotlin { jvmToolchain(17) }

dependencies {
    testImplementation("junit:junit:4.13.2")
    implementation(project(":calendarlib"))
    implementation("com.kosherjava:zmanim:3.0.0")
    implementation("androidx.appcompat:appcompat:1.7.1")
    implementation("androidx.core:core-ktx:1.17.0")
}
