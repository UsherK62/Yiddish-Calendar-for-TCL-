// Builds the forked kizitonwose Calendar (MIT) core + data + view modules as one library.
plugins {
    id("com.android.library")
    id("org.jetbrains.kotlin.android")
}

val upstream = rootProject.file("Calendar")

android {
    namespace = "com.kizitonwose.calendar.view" // keeps R.styleable.CalendarView_* working
    compileSdk = 36
    defaultConfig { minSdk = 21 } // upstream kizitonwose Calendar declares 23; our core/data/view subset is lint-checked at 21
    sourceSets["main"].apply {
        manifest.srcFile("src/main/AndroidManifest.xml")
        java.srcDirs(
            upstream.resolve("core/src/main/java"),
            upstream.resolve("data/src/main/java"),
            upstream.resolve("view/src/main/java"),
        )
        res.srcDirs(upstream.resolve("view/src/main/res"))
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
        isCoreLibraryDesugaringEnabled = true
    }
}

kotlin { jvmToolchain(17) }

dependencies {
    coreLibraryDesugaring("com.android.tools:desugar_jdk_libs:2.1.5")
    api("androidx.recyclerview:recyclerview:1.4.0")
    implementation("androidx.core:core-ktx:1.17.0")
    // Only for the @Immutable annotation used in core; not packaged.
    compileOnly("androidx.compose.runtime:runtime:1.10.1")
}
