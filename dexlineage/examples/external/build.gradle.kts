plugins { kotlin("jvm") }

// Deliberately no project dependencies: compilation must succeed against the SDK
// distribution, without Kotlin test friendship with any production module.
val sdkJars = rootProject.fileTree("build/install/dexlineage/lib") { include("*.jar") }
dependencies { implementation(sdkJars) }

tasks.named("compileKotlin") { dependsOn(":dexlineage:cli:installDist") }
tasks.named("compileTestKotlin") { dependsOn(":dexlineage:cli:installDist") }
tasks.named<Test>("test") {
    dependsOn(tasks.jar)
    systemProperty("example.jar", tasks.jar.get().archiveFile.get().asFile.absolutePath)
    systemProperty("example.sdk", rootProject.file("build/install/dexlineage/lib").absolutePath)
    workingDir(rootProject.projectDir)
}
tasks.register<Test>("fixtureIntegrationTest") {
    group = "verification"
    description = "Exercise an external extension compiled only against installed public JARs."
    dependsOn(tasks.jar, ":dexlineage:cli:buildFixtureApks")
    testClassesDirs = sourceSets.test.get().output.classesDirs
    classpath = sourceSets.test.get().runtimeClasspath
    useJUnitPlatform { includeTags("fixture-integration") }
    systemProperty("example.jar", tasks.jar.get().archiveFile.get().asFile.absolutePath)
    systemProperty("example.sdk", rootProject.file("build/install/dexlineage/lib").absolutePath)
    systemProperty("example.fixtures", rootProject.file("dexlineage/fixtures/android/build/fixture-inputs").absolutePath)
    systemProperty("example.results", layout.buildDirectory.dir("fixture-results").get().asFile.absolutePath)
    workingDir(rootProject.projectDir)
    maxHeapSize = "3g"
}
