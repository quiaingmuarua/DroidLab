import com.android.build.api.artifact.SingleArtifact

plugins {
    id("com.android.application")
    id("com.google.protobuf")
}

androidComponents.onVariants(androidComponents.selector().withBuildType("release")) { variant ->
    val apkDirectory = variant.artifacts.get(SingleArtifact.APK)
    val loader = variant.artifacts.getBuiltArtifactsLoader()
    val currentApk = apkDirectory.map { directory ->
        val artifacts = requireNotNull(loader.load(directory)) { "Fixture APK metadata is missing" }
        require(artifacts.elements.size == 1) { "Fixture export requires exactly one APK" }
        val apk = file(artifacts.elements.single().outputFile)
        require(apk.isFile && apk.toPath().normalize().startsWith(directory.asFile.toPath().normalize())) {
            "Fixture APK metadata must identify a file inside its Android artifact directory"
        }
        apk
    }
    tasks.register<Sync>("export${variant.name.replaceFirstChar { it.uppercase() }}FixtureApk") {
        group = "verification"
        description = "Export the current ${variant.name} APK as a stable input to static fixture tests."
        from(currentApk) { rename { "fixture-${variant.name}.apk" } }
        into(layout.buildDirectory.dir("fixture-inputs/${variant.name}"))
    }
}

dependencies { implementation("com.google.protobuf:protobuf-javalite:3.25.3") }

protobuf {
    protoc { artifact = "com.google.protobuf:protoc:3.25.3" }
    generateProtoTasks {
        all().configureEach {
            builtins { create("java") { option("lite") } }
        }
    }
}

android {
    enableKotlin = false
    namespace = "dev.dexlineage.fixture"
    compileSdk = 35
    buildToolsVersion = "36.0.0"
    defaultConfig {
        applicationId = "dev.dexlineage.fixture"
        minSdk = 26
        targetSdk = 35
        versionCode = 1
        versionName = "fixture"
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    buildFeatures {
        buildConfig = false
        resValues = false
    }
    // Source schemas (including dependency schemas) are test input, never matcher input.
    packaging.resources.excludes += "**/*.proto"
    flavorDimensions += "fixtureVersion"
    productFlavors {
        create("v1") {
            dimension = "fixtureVersion"
            applicationIdSuffix = ".v1"
            proguardFile("v1-rules.pro")
        }
        create("v2") {
            dimension = "fixtureVersion"
            applicationIdSuffix = ".v2"
            versionCode = 2
            proguardFile("v2-rules.pro")
        }
    }
    buildTypes {
        release {
            isMinifyEnabled = true
            isShrinkResources = false
            // No signing configuration: these APKs are statically analyzed, never installed.
            proguardFiles("fixture-rules.pro", "protobuf-fixture-rules.pro", "call-observation-rules.pro")
        }
    }
}
