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

rootProject.name = "KidLearn"

// Pure Kotlin (JVM) modules: no Android dependency, unit-testable anywhere.
include(":core:domain")
include(":core:content")
include(":core:sync")
include(":tools:packer")

// Android modules.
include(":core:data")
include(":core:ui")
include(":feature:profiles")
include(":feature:catalog")
include(":feature:lesson")
include(":feature:media")
include(":feature:quiz")
include(":feature:results")
include(":feature:importer")
include(":feature:questions")
include(":feature:sync")
include(":app")
