plugins {
    base
}

val npmExecutable = if (System.getProperty("os.name").startsWith("Windows")) "npm.cmd" else "npm"
val nodeVersion = layout.projectDirectory.file(".node-version").asFile.readText().trim()
val productionManifest = rootProject.layout.projectDirectory.file(".runtime/hookforge/production-manifest.json")
val temporaryDirectory = rootProject.layout.projectDirectory.dir(".runtime/hookforge/tmp")

fun Exec.npm(vararg arguments: String) {
    workingDir(layout.projectDirectory)
    commandLine(npmExecutable, *arguments)
    // Keep test fixtures in this checkout and avoid macOS /var vs /private/var aliases.
    environment("TMPDIR", temporaryDirectory.asFile.absolutePath)
    environment("TMP", temporaryDirectory.asFile.absolutePath)
    environment("TEMP", temporaryDirectory.asFile.absolutePath)
    doFirst { temporaryDirectory.asFile.mkdirs() }
}

val npmCi = tasks.register<Exec>("npmCi") {
    group = "build setup"
    description = "Install HookForge's locked Node dependencies and reviewed Java bridge patch."
    doFirst {
        val actual = providers.exec { commandLine("node", "--version") }.standardOutput.asText.get().trim()
        check(actual == "v$nodeVersion") {
            "HookForge requires Node $nodeVersion from hookforge/.node-version; found $actual. Set PATH before running Gradle."
        }
    }
    npm("ci")
}

val npmCheck = tasks.register<Exec>("npmCheck") {
    group = "verification"
    description = "Run HookForge types, lint, unit/tool tests, generated-host checks and development build."
    dependsOn(npmCi)
    npm("run", "test:ci")
}

val buildProduction = tasks.register<Exec>("buildProduction") {
    group = "build"
    description = "Build HookForge's production agent and Node CLI."
    dependsOn(npmCi)
    // test:ci also writes dist through build:dev. Production must be the last writer.
    mustRunAfter(npmCheck)
    npm("run", "build:prod")
}

val writeProductionArtifact = tasks.register<Exec>("writeProductionArtifact") {
    group = "verification"
    description = "Record this DroidLab commit and HookForge production bytes."
    dependsOn(buildProduction)
    npm("run", "artifact:write", "--", productionManifest.asFile.absolutePath)
}

tasks.register<Exec>("verifyProductionArtifact") {
    group = "verification"
    description = "Build, record and verify HookForge's production artifact under DroidLab .runtime."
    dependsOn(writeProductionArtifact)
    npm("run", "artifact:verify", "--", productionManifest.asFile.absolutePath)
}

tasks.named("check") {
    dependsOn(npmCheck)
}

tasks.named("assemble") {
    dependsOn(buildProduction)
}

tasks.named<Delete>("clean") {
    delete(layout.projectDirectory.dir("dist"))
}
