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
    maven { url = uri("https://jitpack.io") }
    maven { url = uri("https://maven.aliyun.com/repository/public/") }
  }
}

rootProject.name = "PanaLink V2.0"

include(":app")
