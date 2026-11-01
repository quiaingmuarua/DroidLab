import com.android.build.gradle.internal.tasks.BaseR8Task

plugins {
    base
    alias(libs.plugins.kotlin.jvm) apply false
    alias(libs.plugins.android.application) apply false
    alias(libs.plugins.android.library) apply false
    alias(libs.plugins.protobuf) apply false
}

val androidEnabled = gradle.extra["androidEnabled"] as Boolean
val selectedR8Profile = gradle.extra["droidLabR8Profile"] as String
val fixtureR8Shape = gradle.extra["droidLabFixtureR8Shape"] as String
extra["xaMassWorkerSdkVersion"] = gradle.extra["xaMassWorkerSdkVersion"]

// Execution-profile JVM options are not an APK content fingerprint in AGP.
// Bind the fixture's R8 outputs to the selected compilation shape explicitly.
subprojects {
    tasks.withType<BaseR8Task>().configureEach {
        if (project.path == ":dexlineage:fixture-apk") {
            inputs.property("droidLabR8Profile", selectedR8Profile)
            inputs.property("droidLabR8Shape", if (selectedR8Profile == "fixture") fixtureR8Shape else "normal")
        }
    }
}

// BaseR8Task is used only to classify tasks for the pinned AGP version; its
// finalized tool/execution properties are not modified or unlocked.
gradle.taskGraph.whenReady {
    if (selectedR8Profile == "fixture") {
        val outsideFixture = allTasks.filterIsInstance<BaseR8Task>()
            .filter { it.project.path != ":dexlineage:fixture-apk" }
        check(outsideFixture.isEmpty()) {
            "The fixture R8 execution profile is restricted to :dexlineage:fixture-apk; " +
                "run other Android R8 tasks separately: " + outsideFixture.joinToString { it.path }
        }
    }
}

val python = providers.gradleProperty("pythonExecutable").getOrElse(
    if (System.getProperty("os.name").startsWith("Windows")) "python" else "python3"
)

val prepareXaSdk = tasks.register<Exec>("prepareXaSdk") {
    group = "build setup"
    description = "Resolve and verify the immutable XA Mass Worker SDK."
    workingDir(rootDir)
    commandLine(python, "-m", "droidlab_tools", "release", "resolve",
        "--component", "worker-sdk", "--lock", file("XA_MASS_RELEASE.lock.json").absolutePath)
}
val prepareDuckSdk = tasks.register<Exec>("prepareDuckSdk") {
    group = "build setup"
    description = "Resolve and verify the locked Duck detector AAR."
    workingDir(rootDir)
    commandLine(python, "-m", "droidlab_tools", "duck-sdk", "resolve")
}
tasks.register("prepareDependencies") {
    group = "build setup"
    description = "Prepare locked npm, XA SDK and Duck dependencies (no Android SDK required)."
    dependsOn(prepareXaSdk, prepareDuckSdk, ":hookforge:npmCi")
}
val toolsCheck = tasks.register<Exec>("toolsCheck") {
    group = "verification"
    description = "Test the public build-input resolvers."
    workingDir(rootDir)
    commandLine(python, "-m", "unittest", "discover", "-s", "tests", "-p", "test_*.py", "-v")
}
val docsCheck = tasks.register<Exec>("docsCheck") {
    group = "verification"
    description = "Check repository documentation links and unified build boundaries."
    workingDir(rootDir)
    commandLine(python, "scripts/check_repository.py")
}

tasks.named("check") {
    dependsOn(":dexlineage:check", ":hookforge:check", toolsCheck, docsCheck)
    if (androidEnabled) dependsOn(":labs:check")
}
tasks.named("assemble") {
    dependsOn(":dexlineage:assemble", ":hookforge:assemble")
    if (androidEnabled) dependsOn(":labs:assemble")
}
tasks.named<Delete>("clean") {
    dependsOn(":dexlineage:clean", ":hookforge:clean")
    if (androidEnabled) dependsOn(":labs:clean", ":dexlineage:fixture-apk:clean")
    // Clean remains useful on JVM/Node-only machines after an Android build.
    delete(file("dexlineage/fixtures/android/build"))
    listOf("detect-env", "android-lab", "crackme-native-lib").forEach {
        delete(file("labs/$it/build"))
    }
}
