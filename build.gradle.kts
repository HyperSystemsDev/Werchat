plugins {
    java
}

group = "com.werchat"
version = "1.1.8"

java {
    // Note: Compiling with Java 25, targeting Java 21 bytecode for Hytale compatibility
    sourceCompatibility = JavaVersion.VERSION_21
    targetCompatibility = JavaVersion.VERSION_21
}

repositories {
    mavenCentral()
}

dependencies {
    // Hytale Server API - place HytaleServer.jar in libs/ directory
    compileOnly(files("libs/HytaleServer.jar"))
    implementation("com.google.code.gson:gson:2.10.1")
}

val pluginManifestFile = layout.projectDirectory.file("src/main/resources/manifest.json").asFile

tasks.register("validateManifestServerVersion") {
    inputs.file(pluginManifestFile)
    doLast {
        val manifestText = pluginManifestFile.readText()
        val serverVersionMatch = Regex("\"ServerVersion\"\\s*:\\s*\"([^\"]+)\"").find(manifestText)
            ?: throw GradleException("manifest.json is missing ServerVersion")
        val serverVersion = serverVersionMatch.groupValues[1].trim()
        if (serverVersion.isEmpty() || serverVersion == "*") {
            throw GradleException(
                "manifest.json ServerVersion must be an explicit semver range " +
                    "(for example: \"=2026.02.17-255364b8e\")."
            )
        }
    }
}

tasks.processResources {
    dependsOn("validateManifestServerVersion")
}

tasks.jar {
    manifest {
        attributes(
            "Main-Class" to "com.werchat.WerchatPlugin"
        )
    }
    // Include Gson in the JAR
    from(configurations.runtimeClasspath.get().map { if (it.isDirectory) it else zipTree(it) })
    duplicatesStrategy = DuplicatesStrategy.EXCLUDE
}

// Auto-deploy to Hytale Mods folder after build
tasks.register<Copy>("deploy") {
    dependsOn(tasks.jar)
    from(tasks.jar.get().archiveFile)
    into(System.getenv("APPDATA") + "/Hytale/UserData/Mods")
    doLast {
        println("Deployed to Hytale Mods folder!")
    }
}

// Make build also deploy
tasks.build {
    finalizedBy("deploy")
}
