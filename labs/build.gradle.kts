plugins {
    base
}

val pythonExecutable = providers.gradleProperty("pythonExecutable").orElse(
    if (System.getProperty("os.name").startsWith("Windows", ignoreCase = true)) "python" else "python3"
)

fun registerInputVerification(name: String, component: String) = tasks.register<Exec>(name) {
    group = "verification"
    description = "Verifies the prepared $component bytes without downloading or changing files."
    workingDir(rootProject.projectDir)
    commandLine(
        pythonExecutable.get(), "-m", "droidlab_tools", "verify",
        "--component", component,
        "--lock", rootProject.file("XA_MASS_RELEASE.lock.json").absolutePath
    )
}

val verifyWorkerSdk = registerInputVerification("verifyWorkerSdk", "worker-sdk")
val verifyDuckSdk = registerInputVerification("verifyDuckSdk", "duck-sdk")

tasks.register("verifyDependencies") {
    group = "verification"
    description = "Verifies all prepared Android lab inputs without downloading."
    dependsOn(verifyWorkerSdk, verifyDuckSdk)
}

val labModules = listOf("detect-env", "android-lab", "crackme-native-lib")
val androidEnabled = gradle.extra["androidEnabled"] as Boolean
tasks.named("check") {
    if (androidEnabled) {
        dependsOn(labModules.map { ":labs:$it:testDebugUnitTest" })
    } else {
        doLast { throw GradleException("Android labs are disabled by -Pandroid=false. Omit that option to run :labs:check.") }
    }
}
tasks.named("assemble") {
    if (androidEnabled) {
        dependsOn(labModules.map { ":labs:$it:assembleDebug" })
    } else {
        doLast { throw GradleException("Android labs are disabled by -Pandroid=false. Omit that option to run :labs:assemble.") }
    }
}
tasks.named<Delete>("clean") {
    if (androidEnabled) {
        dependsOn(labModules.map { ":labs:$it:clean" })
    } else {
        delete(labModules.map { layout.projectDirectory.dir("$it/build") })
    }
}
