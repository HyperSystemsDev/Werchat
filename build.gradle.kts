import java.net.URI

plugins {
    java
}

group = "com.werchat"
version = "1.11.1"

fun resolveHytaleVersion(channel: String): String {
    val metadataUrl = "https://maven.hytale.com/$channel/com/hypixel/hytale/Server/maven-metadata.xml"
    val metadataText = URI.create(metadataUrl).toURL().readText()
    val releaseMatch = Regex("<release>(.+?)</release>").find(metadataText)
        ?: throw GradleException("Could not resolve Hytale server version from $metadataUrl")
    return releaseMatch.groupValues[1].trim()
}

fun validateExactServerVersion(version: String) {
    if (version.isBlank()) {
        throw GradleException("Resolved Hytale server version is blank")
    }
    if (version == "*" || version.startsWith("=") || version.startsWith("<") ||
        version.startsWith(">") || version.startsWith("^") || version.startsWith("~")
    ) {
        throw GradleException("Hytale server version must be an exact value, got \"$version\"")
    }
    if (!Regex("^\\d{4}\\.\\d{2}\\.\\d{2}-[A-Za-z0-9]+$").matches(version)) {
        throw GradleException(
            "Hytale server version \"$version\" does not match expected format YYYY.MM.DD-buildHash"
        )
    }
}

val hytaleChannel = ((findProperty("hytale_channel") as String?)?.trim()).orEmpty().ifBlank { "release" }
if (hytaleChannel != "release" && hytaleChannel != "pre-release") {
    throw GradleException("Invalid -Phytale_channel=$hytaleChannel (expected release or pre-release)")
}
val hytaleVersionOverride = ((findProperty("hytale_server_version") as String?)?.trim()).orEmpty().ifBlank { null }
val hytaleVersion = hytaleVersionOverride ?: resolveHytaleVersion(hytaleChannel)
validateExactServerVersion(hytaleVersion)

logger.lifecycle("Building Werchat for Hytale channel=$hytaleChannel, ServerVersion=$hytaleVersion")

java {
    // Note: Compiling with Java 25, targeting Java 21 bytecode for Hytale compatibility
    sourceCompatibility = JavaVersion.VERSION_21
    targetCompatibility = JavaVersion.VERSION_21
}

repositories {
    mavenCentral()
    maven("https://maven.hytale.com/$hytaleChannel")
    maven("https://repo.helpch.at/releases")
}

dependencies {
    compileOnly("com.hypixel.hytale:Server:$hytaleVersion")
    compileOnly("at.helpch:placeholderapi-hytale:1.0.6")
    implementation("com.google.code.gson:gson:2.10.1")
}

tasks.processResources {
    inputs.property("server_version", hytaleVersion)
    filesMatching("manifest.json") {
        expand(mapOf("server_version" to hytaleVersion))
    }
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
    val modsDir = file(System.getenv("APPDATA") + "/Hytale/UserData/Mods")
    into(modsDir)
    doFirst {
        modsDir.listFiles()?.forEach { file ->
            if (file.name.startsWith("Werchat-") && file.name.endsWith(".jar")) {
                if (!file.delete()) {
                    println("Warning: Could not delete old jar ${file.name} (possibly in use)")
                }
            }
        }
    }
    doLast {
        println("Deployed to Hytale Mods folder!")
    }
}

// Make build also deploy
tasks.build {
    finalizedBy("deploy")
}

tasks.register<GradleBuild>("buildRelease") {
    group = "build"
    description = "Clean build against the latest Hytale release server"
    tasks = listOf("clean", "build")
    startParameter.projectProperties = mapOf("hytale_channel" to "release")
}

tasks.register<GradleBuild>("buildPreRelease") {
    group = "build"
    description = "Clean build against the latest Hytale pre-release server"
    tasks = listOf("clean", "build")
    startParameter.projectProperties = mapOf("hytale_channel" to "pre-release")
}
