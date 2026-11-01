plugins {
    kotlin("jvm")
    `java-library`
}

dependencies {
    api(project(":dexlineage:knowledge"))
    implementation(project(":dexlineage:jadx"))
    testRuntimeOnly("org.slf4j:slf4j-simple:2.0.13")
}
