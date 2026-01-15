
import com.android.build.api.variant.FilterConfiguration
import org.jetbrains.kotlin.gradle.dsl.JvmTarget

plugins {
  alias(libs.plugins.ksp)
  alias(libs.plugins.android.application)
  alias(libs.plugins.jetbrains.kotlin.android)
  alias(libs.plugins.kotlin.compose.compiler)
  alias(libs.plugins.room)
  alias(libs.plugins.aboutlibraries)
  alias(libs.plugins.kotlinx.serialization)
}

android {
  namespace = "app.marlboroadvance.mpvex"
  compileSdk = 36

  defaultConfig {
    applicationId = "app.marlboroadvance.mpvex"
    minSdk = 28
    targetSdk = 36
    versionCode = 127
    versionName = "1.2.7"

    vectorDrawables {
      useSupportLibrary = true
    }

    buildConfigField("String", "GIT_SHA", "\"${getCommitSha()}\"")
    buildConfigField("int", "GIT_COUNT", getCommitCount())
  }
  dependenciesInfo {
    includeInApk = false
    includeInBundle = false
  }
  splits {
    abi {
      isEnable = true
      reset()
      include("armeabi-v7a", "arm64-v8a", "x86", "x86_64")
      isUniversalApk = true
    }
  }

  buildTypes {
    named("release") {
      isMinifyEnabled = true
      isShrinkResources = true
      proguardFiles(
         getDefaultProguardFile("proguard-android-optimize.txt"),
        "proguard-rules.pro",
      )
      ndk {
        debugSymbolLevel = "none" // or 'minimal' if needed for crash reports
      }
    }
    create("preview") {
      initWith(getByName("release"))

      signingConfig = signingConfigs["debug"]
      applicationIdSuffix = ".preview"
      versionNameSuffix = "-${getCommitCount()}"
    }
    named("debug") {
      applicationIdSuffix = ".debug"
      versionNameSuffix = "-${getCommitCount()}"
    }
  }
  compileOptions {
    sourceCompatibility = JavaVersion.VERSION_17
    targetCompatibility = JavaVersion.VERSION_17
  }
  buildFeatures {
    compose = true
    viewBinding = true
    buildConfig = true
  }
  composeCompiler {
    includeSourceInformation = true
  }
  packaging {
    resources {
      excludes += "/META-INF/{AL2.0,LGPL2.1}"
      excludes += "META-INF/DEPENDENCIES"
      excludes += "META-INF/LICENSE"
      excludes += "META-INF/LICENSE.txt"
      excludes += "META-INF/LICENSE.md"
      excludes += "META-INF/license.txt"
      excludes += "META-INF/NOTICE"
      excludes += "META-INF/NOTICE.txt"
      excludes += "META-INF/NOTICE.md"
      excludes += "META-INF/notice.txt"
      excludes += "META-INF/ASL2.0"
      excludes += "META-INF/*.kotlin_module"
      excludes += "META-INF/versions/9/OSGI-INF/MANIFEST.MF"
    }
    jniLibs {
      useLegacyPackaging = true
    }
  }

  val abiCodes =
    mapOf(
      "armeabi-v7a" to 1,
      "arm64-v8a" to 2,
      "x86" to 3,
      "x86_64" to 4,
    )
  androidComponents {
    onVariants { variant ->
      variant.outputs.forEach { output ->
        val abi = output.filters.find { it.filterType == FilterConfiguration.FilterType.ABI }?.identifier
        output.versionCode.set((output.versionCode.orNull ?: 0) * 10 + (abiCodes[abi] ?: 0))
      }
    }
  }
  @Suppress("UnstableApiUsage")
  androidResources {
    generateLocaleConfig = true
  }
}

kotlin {
  compilerOptions {
    freeCompilerArgs.addAll(
      "-Xwhen-guards",
      "-Xcontext-parameters",
      "-Xannotation-default-target=param-property",
      "-opt-in=com.google.accompanist.permissions.ExperimentalPermissionsApi",
      "-opt-in=androidx.compose.material3.ExperimentalMaterial3Api",
    )
    jvmTarget.set(JvmTarget.JVM_17)
  }
}

room {
  schemaDirectory("$projectDir/schemas")
}

dependencies {
  implementation(libs.androidx.activity.compose)
  implementation(platform(libs.androidx.compose.bom))
  implementation(libs.androidx.ui)
  implementation(libs.androidx.ui.graphics)
  implementation(libs.androidx.material3.android)
  implementation(libs.androidx.compose.material)
  implementation(libs.androidx.ui.tooling.preview)
  debugImplementation(libs.androidx.ui.tooling)
  implementation(libs.bundles.compose.navigation3)
  implementation(libs.androidx.appcompat)
  implementation(libs.androidx.compose.constraintlayout)
  implementation(libs.androidx.material3.icons.extended)
  implementation(libs.androidx.compose.animation.graphics)
  implementation(libs.material)
  implementation(libs.mediasession)
  implementation(libs.androidx.preferences.ktx)
  implementation(libs.androidx.documentfile)
  implementation(libs.saveable)


  implementation(platform(libs.koin.bom))
  implementation(libs.bundles.koin)

  implementation(libs.seeker)
  implementation(libs.compose.prefs)
  implementation(libs.aboutlibraries.compose.m3)

  implementation(libs.accompanist.permissions)

  implementation(libs.room.runtime)
  ksp(libs.room.compiler)
  implementation(libs.room.ktx)

  implementation(libs.kotlinx.immutable.collections)
  implementation(libs.kotlinx.serialization.json)
  implementation(libs.okhttp)
  implementation(libs.okhttp.logging)

  implementation(libs.truetype.parser)
  implementation(libs.fsaf)
  implementation(libs.mediainfo.lib)
  implementation(files("libs/mpv-android-lib-v0.0.3.aar"))

  // Network protocol libraries
  implementation(libs.smbj) // SMB/CIFS
  implementation(libs.commons.net) // FTP
  implementation(libs.sardine.android) { 
    exclude(group = "xpp3", module = "xpp3")
  }
  implementation(libs.nanohttpd)
  implementation(libs.lazycolumnscrollbar)
  implementation(libs.reorderable)

  implementation(libs.coil.core)
  implementation(libs.coil.compose)
}

fun getCommitCount(): String = runCommand("git rev-list --count HEAD") ?: "0"

fun getCommitSha(): String = runCommand("git rev-parse --short HEAD") ?: "unknown"

fun runCommand(command: String): String? =
  try {
    val parts = command.split(' ')
    val process =
      ProcessBuilder(parts)
        .redirectErrorStream(true)
        .start()
    val output =
      process.inputStream
        .bufferedReader()
        .readText()
        .trim()
    process.waitFor()
    output.ifEmpty { null }
  } catch (e: Exception) {
    null
  }
