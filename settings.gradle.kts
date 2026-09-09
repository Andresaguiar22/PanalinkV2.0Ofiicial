pluginManagement {
  repositories {
    google()
    maven { url = uri("https://plugins.gradle.org/m2/") }
    mavenCentral()
  }
}


dependencyResolutionManagement {
  repositoriesMode.set(RepositoriesMode.FAIL_ON_PROJECT_REPOS)
  repositories {
    google()
    mavenCentral()
    // JitPack — required transitively by io.livekit:livekit-android (audioswitch).
    maven { url = uri("https://jitpack.io") }
  }
}

rootProject.name = "PanaLink V2.0"

include(":app")
