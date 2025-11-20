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
    maven(url = "https://www.jitpack.io") {
      content {
        // Only use JitPack for specific dependencies to avoid unnecessary checks
        includeGroup("io.github.abdallahmehiz")
        includeGroup("com.github.abdallahmehiz")
        includeGroup("com.github.K1rakishou")
        includeGroup("com.github.marlboro-advance")
        includeGroup("com.github.thegrizzlylabs")
        includeGroup("com.github.nanihadesuka")
      }
    }
  }
}

rootProject.name = "mpvEx"
include(":app")
