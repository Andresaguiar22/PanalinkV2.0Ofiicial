pluginManagement {
  repositories {
    google()
    maven { url = uri("https://plugins.gradle.org/m2/") }
    mavenCentral()
    maven { url = uri("https://jitpack.io") }
    maven { url = uri("https://maven.aliyun.com/repository/public/") }
    maven { url = uri("https://repo.huaweicloud.com/repository/maven/") }
  }
}


dependencyResolutionManagement {
  repositoriesMode.set(RepositoriesMode.FAIL_ON_PROJECT_REPOS)
  repositories {
    google()
    mavenCentral()
    maven { url = uri("https://jitpack.io") }
    maven { url = uri("https://maven.aliyun.com/repository/public/") }
    maven { url = uri("https://repo.huaweicloud.com/repository/maven/") }
  }
}

rootProject.name = "PanaLink V2.0"

include(":app")
