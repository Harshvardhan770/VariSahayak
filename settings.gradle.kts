pluginManagement {
    repositories {
        google {
            content {
                includeGroupByRegex("com\\.android.*")
                includeGroupByRegex("com\\.google.*")
                includeGroupByRegex("androidx.*")
            }
        }
        mavenCentral()
        gradlePluginPortal()
    }
}

dependencyResolutionManagement {
    repositoriesMode.set(RepositoriesMode.FAIL_ON_PROJECT_REPOS)
    repositories {
        google()
        mavenCentral()

        // LiveKit's audio routing dependency (com.github.davidliu:audioswitch) is published
        // only on JitPack, pinned by livekit-android to a commit hash. Scoped to that one
        // group so JitPack — which builds arbitrary GitHub repositories on demand — is never
        // consulted for anything else in the tree.
        maven {
            url = uri("https://jitpack.io")
            content { includeGroup("com.github.davidliu") }
        }
    }
}

rootProject.name = "VariSahayak"
include(":app")
