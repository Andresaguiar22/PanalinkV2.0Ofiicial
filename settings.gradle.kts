pluginManagement {
  repositories {
    google()
    maven { url = uri("https://plugins.gradle.org/m2/") }
    maven { url = uri("https://repo.huaweicloud.com/repository/maven/") }
    maven { url = uri("https://maven.aliyun.com/repository/public/") }
    mavenCentral()
    maven { url = uri("https://jitpack.io") }
  }
}


dependencyResolutionManagement {
  repositoriesMode.set(RepositoriesMode.FAIL_ON_PROJECT_REPOS)
  repositories {
    google()
    maven { url = uri("https://jitpack.io") }
    maven { url = uri("https://repo.huaweicloud.com/repository/maven/") }
    maven { url = uri("https://maven.aliyun.com/repository/public/") }
    mavenCentral()
  }
}

rootProject.name = "PanaLink V2.0"

include(":app")
