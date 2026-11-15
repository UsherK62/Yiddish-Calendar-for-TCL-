pluginManagement {
    repositories {
        google()
        mavenCentral()
        gradlePluginPortal()
    }
}
dependencyResolutionManagement {
    repositoriesMode.set(RepositoriesMode.FAIL_ON_PROJECT_REPOS)
    repositories {
        google()
        mavenCentral()
    }
}
rootProject.name = "YiddishCalendar"
// kizitonwose Calendar core+data+view sources (forked in ./Calendar), built as one Android library.
include(":calendarlib")
include(":app")
