// Kover merges every module's unit-test coverage into one report: ./gradlew koverHtmlReport
plugins {
    id("org.jetbrains.kotlinx.kover")
}

dependencies {
    kover(project(":common"))
    kover(project(":common-media"))
    kover(project(":common-downloads"))
    kover(project(":app-audiovault"))
    kover(project(":app-bookvault"))
    kover(project(":app-livevault"))
    kover(project(":app-tunevault"))
}

kover {
    reports {
        filters {
            excludes {
                // Generated code: view binding, Room DAOs/databases, BuildConfig, Compose lambdas
                classes("*.databinding.*", "*_Impl", "*_Impl$*", "*.BuildConfig", "*.ComposableSingletons*")
            }
        }
    }
}

allprojects {
    repositories {
        mavenCentral()
        google()
        mavenLocal {
            content {
                includeVersionByRegex(JellyfinSdk.GROUP, ".*", JellyfinSdk.LOCAL)
            }
        }
        maven("https://s01.oss.sonatype.org/content/repositories/snapshots/") {
            content {
                includeVersionByRegex(JellyfinSdk.GROUP, ".*", JellyfinSdk.SNAPSHOT)
                includeVersionByRegex(JellyfinSdk.GROUP, ".*", JellyfinSdk.SNAPSHOT_UNSTABLE)
                includeVersionByRegex(JellyfinMedia3.GROUP, ".*", JellyfinMedia3.SNAPSHOT)
            }
        }
    }
}

tasks.register<Delete>("clean") {
    delete(rootProject.layout.buildDirectory)
}
