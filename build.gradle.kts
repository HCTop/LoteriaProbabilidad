// Top-level build file where you can add configuration options common to all sub-projects/modules.
plugins {
    // 8.2.0 solo alineaba las librerías nativas a 4 KB; se necesita 8.5.1+
    // para que el APK quede alineado a 16 KB (soporte de "page size" 16 KB).
    id("com.android.application") version "8.5.2" apply false
    id("org.jetbrains.kotlin.android") version "1.9.20" apply false
    id("com.google.devtools.ksp") version "1.9.20-1.0.14" apply false
}

tasks.register("clean", Delete::class) {
    delete(rootProject.layout.buildDirectory)
}
