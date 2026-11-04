pluginManagement {
    repositories {
        gradlePluginPortal()
        mavenCentral()
        // essential-loom (architectury-loom fork with legacy Forge support)
        maven("https://repo.essential.gg/repository/maven-public")
        maven("https://maven.architectury.dev/")
        maven("https://maven.fabricmc.net")
        maven("https://maven.minecraftforge.net/")
        maven("https://repo.spongepowered.org/maven/")
    }
}

plugins {
    // Lets Gradle fetch the Java 8/17/21 toolchains this build needs when they aren't installed.
    id("org.gradle.toolchains.foojay-resolver-convention") version ("1.0.0")
}

rootProject.name = "lwjgl3ify"
