plugins {
    id("com.android.application") version "8.7.3" apply false
    id("org.jetbrains.kotlin.android") version "2.0.21" apply false
    id("org.jetbrains.kotlin.plugin.compose") version "2.0.21" apply false
    id("com.google.devtools.ksp") version "2.0.21-1.0.27" apply false
}

rootProject.layout.buildDirectory.set(
    rootProject.layout.projectDirectory.dir("compile/root"),
)
subprojects {
    layout.buildDirectory.set(
        rootProject.layout.projectDirectory.dir("compile/${project.name}"),
    )
}
