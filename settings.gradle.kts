import com.android.build.api.dsl.SettingsExtension
import groovy.json.JsonSlurper

pluginManagement {
    // Settings plugins are resolved before project version-catalog accessors exist.
    // Read the same pinned AGP version instead of maintaining a second version.
    val agpVersion = Regex("(?m)^agp\\s*=\\s*\"([^\"]+)\"\\s*$")
        .find(file("gradle/libs.versions.toml").readText())?.groupValues?.get(1)
        ?: error("gradle/libs.versions.toml must declare the AGP version")
    plugins {
        id("com.android.settings") version agpVersion
    }
    repositories {
        google()
        gradlePluginPortal()
        mavenCentral()
    }
}

plugins {
    id("com.android.settings")
}

rootProject.name = "DroidLab"

// AGP profiles apply to the invocation, so opt in only through explicit fixture
// entrypoints. The root task-graph guard rejects other R8 tasks in this profile.
val fixtureCompilationRequested = gradle.startParameter.taskNames.any {
    it.substringAfterLast(':') in setOf("fixtureIntegrationTest", "buildFixtureApks")
}
val selectedR8Profile = if (fixtureCompilationRequested) "fixture" else "normal"
listOf("android.settings.executionProfile", "android.experimental.settings.executionProfile").forEach { key ->
    providers.gradleProperty(key).orNull?.let { requestedProfile ->
        check(requestedProfile == selectedR8Profile) {
            "DroidLab selects R8 profile '$selectedR8Profile' from the requested tasks; remove conflicting -P$key=$requestedProfile."
        }
    }
}
gradle.extra["droidLabR8Profile"] = selectedR8Profile
gradle.extra["droidLabFixtureR8Shape"] = "R8 9.4.24: com.android.tools.r8.outliner.enable=false"

extensions.configure<SettingsExtension> {
    execution {
        profiles {
            create("normal") {
                r8 { runInSeparateProcess = false }
            }
            create("fixture") {
                r8 {
                    runInSeparateProcess = true
                    // R8 9.4.24 BottomUpOutlinerOptions: controls BUOutline,
                    // not API-model/backport outlining. Never set daemon flags.
                    jvmOptions.add("-Dcom.android.tools.r8.outliner.enable=false")
                }
            }
        }
        defaultProfile = selectedR8Profile
    }
}

val androidEnabled = providers.gradleProperty("android")
    .map { it.toBooleanStrict() }.getOrElse(true)
gradle.extra["androidEnabled"] = androidEnabled

val xaLock = JsonSlurper().parse(file("XA_MASS_RELEASE.lock.json")) as Map<*, *>
val xaVersion = xaLock["version"] as? String
    ?: error("XA_MASS_RELEASE.lock.json must contain a version")
check(Regex("[0-9]+\\.[0-9]+\\.[0-9]+").matches(xaVersion)) {
    "Invalid XA Mass SDK version in the release lock"
}
gradle.extra["xaMassWorkerSdkVersion"] = xaVersion

dependencyResolutionManagement {
    repositoriesMode.set(RepositoriesMode.FAIL_ON_PROJECT_REPOS)
    repositories {
        exclusiveContent {
            forRepository {
                maven {
                    name = "xaMassWorkerSdk"
                    url = uri(file(".runtime/xa-mass-worker-sdk/repository"))
                }
            }
            filter { includeGroup("com.xa.mass") }
        }
        google()
        mavenCentral()
        exclusiveContent {
            forRepository { maven("https://jitpack.io") }
            filter { includeGroup("com.github.Tencent.soter") }
        }
        maven("https://maven.aliyun.com/repository/public")
        maven("https://maven.aliyun.com/repository/google")
        maven("https://nexus.iinti.cn/repository/maven-public/")
    }
}

include(":dexlineage", ":hookforge")
listOf("knowledge", "jadx", "analysis", "migration", "cli").forEach {
    include(":dexlineage:$it")
}
// A separately compiled consumer of installed JARs; it is not a production module.
include(":dexlineage:external-example")
project(":dexlineage:external-example").projectDir = file("dexlineage/examples/external")
if (androidEnabled) {
    include(":labs")
    include(":dexlineage:fixture-apk")
    project(":dexlineage:fixture-apk").projectDir = file("dexlineage/fixtures/android")
    listOf("detect-env", "android-lab", "crackme-native-lib").forEach {
        include(":labs:$it")
    }
} else {
    check(gradle.startParameter.taskNames.none { it.trimStart(':').startsWith("labs:") }) {
        "Android labs are disabled by -Pandroid=false. Omit that option to run :labs tasks."
    }
}
