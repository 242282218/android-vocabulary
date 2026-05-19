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

rootProject.name = "AndroidVocabulary"

include(":app")
include(":core:model")
include(":core:common")
include(":core:domain")
include(":core:scheduler")
include(":core:database")
include(":core:datastore")
include(":core:vocabulary")
include(":core:stats")
include(":core:designsystem")
include(":core:testing")
include(":feature:today")
include(":feature:review")
include(":feature:wordbook")
include(":feature:stats")
include(":feature:settings")
include(":worker")
