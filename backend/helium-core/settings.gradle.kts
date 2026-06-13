pluginManagement {
    repositories {
        gradlePluginPortal()
        mavenCentral()
    }
}

dependencyResolutionManagement {
    repositoriesMode.set(RepositoriesMode.FAIL_ON_PROJECT_REPOS)
    repositories {
        mavenCentral()
    }
}

rootProject.name = "helium-core"

include(":app")

include(":modules:auth-user")
include(":modules:compliance-lite")
include(":modules:wallet")
include(":modules:ledger")
include(":modules:trading")
include(":modules:matching")
include(":modules:market-data")
include(":modules:admin")
include(":modules:audit")
include(":modules:outbox")

include(":shared:common")
include(":shared:security")
include(":shared:persistence")
include(":shared:observability")
include(":shared:test-support")

