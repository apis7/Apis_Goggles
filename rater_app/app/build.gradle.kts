plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.kotlin.serialization)
}

// The goggles and the news seed list ship as assets, copied from the repo at build
// time so the bundled snapshot never drifts from the files beside this project.
val goggleAssets = layout.buildDirectory.dir("generated/goggleAssets")
val copyGoggles by tasks.registering(Copy::class) {
    from(rootProject.file("../Apis_health/apis_health.goggle"))
    from(rootProject.file("../Apis_news/apis_news.goggle"))
    from(rootProject.file("../Apis_news/middle_east_sources.csv")) { rename { "news_seed.csv" } }
    into(goggleAssets)
}

android {
    namespace = "com.apis.goggles"
    compileSdk = 36

    defaultConfig {
        applicationId = "com.apisgoggles.rater"
        minSdk = 26
        targetSdk = 36
        versionCode = 1
        versionName = "0.1.0"
    }

    sourceSets["main"].assets.srcDir(goggleAssets)

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    kotlinOptions { jvmTarget = "17" }
    buildFeatures { compose = true }
}

tasks.named("preBuild") { dependsOn(copyGoggles) }
tasks.matching { it.name.startsWith("merge") && it.name.endsWith("Assets") }
    .configureEach { dependsOn(copyGoggles) }

dependencies {
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.lifecycle.runtime.compose)
    implementation(libs.androidx.lifecycle.viewmodel.compose)
    implementation(libs.androidx.activity.compose)
    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.ui)
    implementation(libs.androidx.material3)
    implementation(libs.androidx.material.icons.extended)
    implementation(libs.kotlinx.serialization.json)
    implementation(libs.kotlinx.coroutines.android)
    implementation(libs.okhttp)
    implementation(libs.coil.compose)
    implementation(libs.coil.network.okhttp)
    testImplementation(libs.junit)
}
