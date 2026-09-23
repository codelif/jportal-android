// throwaway: m1 sign-in feasibility spike, deleted once the real flow lands
plugins {
    alias(libs.plugins.android.application)
}

android {
    namespace = "in.codelif.jportal.spike"
    compileSdk = 36

    defaultConfig {
        applicationId = "in.codelif.jportal.spike"
        minSdk = 26
        targetSdk = 36
        versionCode = 1
        versionName = "spike"
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
}

dependencies {
    implementation(libs.ktjiit)
    implementation(libs.webkit)
    implementation(libs.activity.compose)
    implementation(libs.coroutines.android)
}
