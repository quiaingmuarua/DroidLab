import org.jetbrains.kotlin.gradle.dsl.KotlinJvmProjectExtension

plugins {
    base
}

group = "dev.dexlineage"
version = "0.4.0"
val kotlinVersion = libs.versions.kotlin.get()

subprojects {
    group = "dev.dexlineage"
    version = "0.4.0"
    dependencyLocking { lockAllConfigurations() }
    pluginManager.withPlugin("org.jetbrains.kotlin.jvm") {
        extensions.configure<KotlinJvmProjectExtension> { jvmToolchain(21) }
        dependencies {
            add("testImplementation", "org.jetbrains.kotlin:kotlin-test-junit5:$kotlinVersion")
            add("testImplementation", "org.junit.jupiter:junit-jupiter:5.11.4")
            add("testRuntimeOnly", "org.junit.platform:junit-platform-launcher:1.11.4")
        }
        tasks.withType<Test>().configureEach { useJUnitPlatform() }
        tasks.named<Test>("test") { useJUnitPlatform { excludeTags("real-case", "fixture-integration") } }
    }
}

val modules = listOf("knowledge", "jadx", "analysis", "migration", "cli")
tasks.register("test") {
    group = "verification"
    description = "Run public-data-only tests in all modules."
    dependsOn(modules.map { ":dexlineage:$it:test" })
}
tasks.named("check") { dependsOn(modules.map { ":dexlineage:$it:check" }) }
tasks.named("assemble") { dependsOn(modules.map { ":dexlineage:$it:assemble" }) }
tasks.named("build") { dependsOn(modules.map { ":dexlineage:$it:build" }); dependsOn(":dexlineage:external-example:test") }
tasks.named<Delete>("clean") {
    dependsOn(modules.map { ":dexlineage:$it:clean" })
    delete(rootProject.layout.buildDirectory.dir("install/dexlineage"))
}
tasks.register("installDist") {
    group = "distribution"
    description = "Install the CLI distribution at build/install/dexlineage."
    dependsOn(":dexlineage:cli:installDist")
}
tasks.register("fixtureIntegrationTest") {
    group = "verification"
    description = "Build public R8 APK fixtures and exercise the installed CLI end to end."
    dependsOn(":dexlineage:cli:fixtureIntegrationTest")
    dependsOn(":dexlineage:external-example:fixtureIntegrationTest")
}
