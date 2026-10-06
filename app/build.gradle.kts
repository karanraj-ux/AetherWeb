plugins {
  alias(libs.plugins.android.application)
  alias(libs.plugins.kotlin.compose)
  alias(libs.plugins.google.devtools.ksp)
  alias(libs.plugins.roborazzi)
  alias(libs.plugins.secrets)
}

android {
  namespace = "com.aetherweb.app"
  compileSdk { version = release(36) { minorApiLevel = 1 } }

  defaultConfig {
    applicationId = "com.aetherweb.app"
    minSdk = 24
    targetSdk = 36
    versionCode = 2
    versionName = "1.1.1"

    testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
  }

  signingConfigs {
    create("release") {
      val keystorePath = System.getenv("KEYSTORE_PATH") ?: "${rootDir}/debug.keystore"
      storeFile = file(keystorePath)
      storePassword = System.getenv("STORE_PASSWORD") ?: "android"
      keyAlias = System.getenv("KEY_ALIAS") ?: "androiddebugkey"
      keyPassword = System.getenv("KEY_PASSWORD") ?: "android"
    }
    create("debugConfig") {
      storeFile = file("${rootDir}/debug.keystore")
      storePassword = "android"
      keyAlias = "androiddebugkey"
      keyPassword = "android"
    }
  }

  buildTypes {
    release {
      isCrunchPngs = false
      isMinifyEnabled = true
      isShrinkResources = true
      proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
      signingConfig = signingConfigs.getByName("release")
    }
    debug { signingConfig = signingConfigs.getByName("debugConfig") }
  }

  // Tier-2 "Seed": chat-only minimal flavor. Shared code stays flavor-agnostic;
  // UI/portal gate on BuildConfig.FLAVOR.
  flavorDimensions += "tier"
  productFlavors {
    create("full") {
      dimension = "tier"
    }
    create("lite") {
      dimension = "tier"
      applicationIdSuffix = ".lite"
      versionNameSuffix = "-lite"
      resValue("string", "app_name", "Aether Seed")
    }
  }

  // Both APKs in the app: the full ("mothership") flavor bundles the lite APK
  // as an asset so its web portal can serve both downloads offline.
  sourceSets {
    named("full") {
      assets.srcDir(layout.buildDirectory.dir("generated/assets/liteSeed"))
    }
  }

  compileOptions {
    sourceCompatibility = JavaVersion.VERSION_11
    targetCompatibility = JavaVersion.VERSION_11
  }
  packaging {
    resources {
      excludes += "/META-INF/{AL2.0,LGPL2.1}"
      excludes += "META-INF/INDEX.LIST"
      excludes += "META-INF/io.netty.versions.properties"
    }
  }
  buildFeatures {
    compose = true
    buildConfig = true
  }
  testOptions { unitTests { isIncludeAndroidResources = true } }
}

// Configure the Secrets Gradle Plugin to use .env and .env.example files
// to match the convention used in Web projects.
secrets {
  propertiesFileName = ".env"
  defaultPropertiesFileName = ".env.example"
}

// Some unused dependencies are commented out below instead of being removed.
// This makes it easy to add them back in the future if needed.
dependencies {
  implementation(libs.zxing.core)
  implementation(libs.ktor.server.core)
  implementation(libs.ktor.server.netty)
  implementation(libs.ktor.server.websockets)
  implementation(libs.ktor.server.content.negotiation)
  implementation(libs.ktor.server.status.pages)
  implementation(libs.ktor.server.compression)
  implementation(libs.ktor.serialization.gson)
  implementation(libs.ktor.server.cors)
  implementation(libs.ktor.server.partial.content)
  implementation(libs.ktor.server.auto.head.response)
  implementation(platform(libs.androidx.compose.bom))
  implementation(libs.accompanist.permissions)
  implementation(libs.androidx.activity.compose)
  // implementation(libs.androidx.camera.camera2)
  // implementation(libs.androidx.camera.core)
  // implementation(libs.androidx.camera.lifecycle)
  // implementation(libs.androidx.camera.view)
  implementation(libs.androidx.compose.material.icons.core)
  implementation(libs.androidx.compose.material.icons.extended)
  implementation(libs.androidx.compose.material3)
  implementation(libs.androidx.compose.ui)
  implementation(libs.androidx.compose.ui.graphics)
  implementation(libs.androidx.compose.ui.tooling.preview)
  implementation(libs.androidx.core.ktx)
  // implementation(libs.androidx.datastore.preferences)
  implementation(libs.androidx.lifecycle.runtime.compose)
  implementation(libs.androidx.lifecycle.runtime.ktx)
  implementation(libs.androidx.lifecycle.viewmodel.compose)
  // implementation(libs.androidx.navigation.compose)
  implementation(libs.androidx.room.ktx)
  implementation(libs.androidx.room.runtime)
  implementation(libs.androidx.security.crypto)
  implementation(libs.coil.compose)
  implementation(libs.converter.moshi)
  implementation(libs.kotlinx.coroutines.android)
  implementation(libs.kotlinx.coroutines.core)
  implementation(libs.logging.interceptor)
  implementation(libs.moshi.kotlin)
  implementation(libs.okhttp)
  implementation(libs.retrofit)
  // Voice Room Phase 0: self-signed HTTPS cert generation for browser mic access
  implementation("org.bouncycastle:bcprov-jdk18on:1.78.1")
  implementation("org.bouncycastle:bcpkix-jdk18on:1.78.1")
  testImplementation(libs.androidx.compose.ui.test.junit4)
  testImplementation(libs.androidx.core)
  testImplementation(libs.androidx.junit)
  testImplementation(libs.junit)
  testImplementation(libs.kotlinx.coroutines.test)
  testImplementation(libs.robolectric)
  testImplementation(libs.roborazzi)
  testImplementation(libs.roborazzi.compose)
  testImplementation(libs.roborazzi.junit.rule)
  androidTestImplementation(platform(libs.androidx.compose.bom))
  androidTestImplementation(libs.androidx.compose.ui.test.junit4)
  androidTestImplementation(libs.androidx.espresso.core)
  androidTestImplementation(libs.androidx.junit)
  androidTestImplementation(libs.androidx.runner)
  debugImplementation(libs.androidx.compose.ui.test.manifest)
  debugImplementation(libs.androidx.compose.ui.tooling)
  "ksp"(libs.androidx.room.compiler)
  "ksp"(libs.moshi.kotlin.codegen)
}

// Both APKs in the app: copy the lite APK into the full flavor's generated
// assets before they are merged, so the mothership portal serves both downloads.
listOf("Debug", "Release").forEach { bt ->
  val lower = bt.lowercase()
  val copyTask = tasks.register("copyLite${bt}ApkForFull", Copy::class) {
    dependsOn("packageLite$bt")
    from(layout.buildDirectory.file("outputs/apk/lite/$lower/app-lite-$lower.apk"))
    into(layout.buildDirectory.dir("generated/assets/liteSeed/seed"))
    rename { "aetherweb-lite.apk" }
  }
  tasks.named("mergeFull${bt}Assets") { dependsOn(copyTask) }
}
