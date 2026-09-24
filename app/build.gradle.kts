plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.kotlin.serialization)
    alias(libs.plugins.baselineprofile)
    alias(libs.plugins.roborazzi)
}

// release builds get their version from the signed tag, see .github/workflows/release.yml
val tag = providers.environmentVariable("JPORTAL_VERSION").orElse("0.1.0").get().removePrefix("v")
val (major, minor, patch) = tag.split('.', '-').take(3).map { it.toIntOrNull() ?: 0 }.let { it + List(3 - it.size) { 0 } }

android {
    namespace = "in.codelif.jportal"
    // compose 1.12 needs 37 headers to build, runtime behaviour follows targetSdk
    compileSdk = 37

    defaultConfig {
        applicationId = "in.codelif.jportal.android"
        minSdk = 26
        targetSdk = 36
        versionCode = major * 10000 + minor * 100 + patch
        versionName = tag
        androidResources.localeFilters += "en"
        buildConfigField("boolean", "DEMO", "false")
    }

    flavorDimensions += "channel"
    productFlavors {
        create("github") {
            dimension = "channel"
            buildConfigField("boolean", "UPDATE_CHECK", "true")
        }
        create("play") {
            dimension = "channel"
            buildConfigField("boolean", "UPDATE_CHECK", "false")
        }
    }

    signingConfigs {
        create("release") {
            val store = providers.environmentVariable("JPORTAL_KEYSTORE").orNull
            if (store != null) {
                storeFile = file(store)
                storePassword = providers.environmentVariable("JPORTAL_KEYSTORE_PASSWORD").get()
                keyAlias = providers.environmentVariable("JPORTAL_KEY_ALIAS").get()
                keyPassword = providers.environmentVariable("JPORTAL_KEY_PASSWORD").get()
            }
        }
    }

    buildTypes {
        release {
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
            signingConfig = signingConfigs.getByName("release").takeIf { it.storeFile != null }
                ?: signingConfigs.getByName("debug")
        }
        debug {
            applicationIdSuffix = ".debug"
        }
    }

    buildFeatures {
        compose = true
        buildConfig = true
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    packaging {
        resources.excludes += listOf("/META-INF/{AL2.0,LGPL2.1}", "DebugProbesKt.bin", "kotlin-tooling-metadata.json", "/META-INF/*.version")
    }

    testOptions {
        unitTests {
            isIncludeAndroidResources = true
            all {
                it.systemProperty("robolectric.pixelCopyRenderMode", "hardware")
                it.maxHeapSize = "2g"
            }
        }
    }

    dependenciesInfo {
        // f-droid and obtainium folks dislike the encrypted blob, play doesn't need it
        includeInApk = false
        includeInBundle = false
    }
}

/** fails the build when a release apk outgrows its budget */
abstract class ApkSizeCheck : DefaultTask() {
    @get:InputFiles
    abstract val apks: DirectoryProperty

    @get:Input
    abstract val budget: Property<Long>

    @TaskAction
    fun check() {
        apks.get().asFile.walk().filter { it.extension == "apk" }.forEach { apk ->
            val size = apk.length()
            logger.lifecycle("${apk.name}: ${size / 1024} KiB of a ${budget.get() / 1024} KiB budget")
            check(size <= budget.get()) { "${apk.name} is ${size / 1024} KiB, over the ${budget.get() / 1024} KiB budget" }
        }
    }
}

// 1.93 MB in september 2026. growing past this should be a decision, not an accident
val apkBudget = 2_250_000L

androidComponents {
    onVariants(selector().withBuildType("release")) { v ->
        val name = v.name.replaceFirstChar { it.uppercase() }
        val check = tasks.register<ApkSizeCheck>("check${name}ApkSize") {
            apks.set(v.artifacts.get(com.android.build.api.artifact.SingleArtifact.APK))
            budget.set(apkBudget)
        }
        tasks.matching { it.name == "assemble$name" }.configureEach { finalizedBy(check) }
    }
    onVariants { v ->
        // benchmarks and profile runs can't script a google sign in, they get the made up student instead
        if (v.buildType == "benchmarkRelease" || v.buildType == "nonMinifiedRelease") {
            v.buildConfigFields?.put("DEMO", com.android.build.api.variant.BuildConfigField("boolean", "true", null))
        }
    }
}

roborazzi {
    // reference images live with the tests, record with -Proborazzi.test.record=true
    outputDir.set(file("src/test/screenshots"))
}

baselineProfile {
    // generated on a device by hand, see baselineprofile/, then checked in
    automaticGenerationDuringBuild = false
    saveInSrc = true
    // one profile for both flavors, they run the same code
    mergeIntoMain = true
    // the demo student never ships, its classes have no business in the profile
    filter { exclude("in.codelif.jportal.demo.**") }
    dexLayoutOptimization = true
}

kotlin {
    compilerOptions {
        optIn.addAll(
            "androidx.compose.material3.ExperimentalMaterial3Api",
            "androidx.compose.material3.ExperimentalMaterial3ExpressiveApi",
            "androidx.compose.animation.ExperimentalSharedTransitionApi",
            "androidx.compose.foundation.layout.ExperimentalLayoutApi",
        )
    }
}

dependencies {
    implementation(libs.ktjiit)
    implementation(platform(libs.compose.bom))
    implementation(libs.compose.ui)
    implementation(libs.compose.foundation)
    implementation(libs.compose.animation)
    implementation(libs.compose.material3)
    implementation(libs.activity.compose)
    implementation(libs.webkit)
    implementation(libs.profileinstaller)
    implementation(libs.splashscreen)
    implementation(libs.serialization.json)
    implementation(libs.coroutines.android)

    testImplementation(libs.junit4)
    testImplementation(libs.robolectric)
    testImplementation(libs.roborazzi)
    testImplementation(libs.roborazzi.compose)
    testImplementation(libs.roborazzi.junit)
    testImplementation(libs.androidx.test.core)
    testImplementation(platform(libs.compose.bom))
    testImplementation(libs.compose.ui.test)
    debugImplementation(libs.compose.ui.test.manifest)
    baselineProfile(project(":baselineprofile"))

    debugImplementation(libs.compose.ui.tooling)
    implementation(libs.compose.ui.tooling.preview)
}
