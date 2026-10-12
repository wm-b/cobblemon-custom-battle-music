plugins {
    id("fabric-loom") version "1.10.5"
    kotlin("jvm") version "2.2.20"
}

version = property("mod_version")!!
group = property("maven_group")!!
base { archivesName.set(property("archives_base_name").toString()) }

repositories {
    mavenCentral()
    exclusiveContent {
        forRepository { maven("https://api.modrinth.com/maven") }
        filter { includeGroup("maven.modrinth") }
    }
}

dependencies {
    minecraft("com.mojang:minecraft:${property("minecraft_version")}")
    mappings(loom.officialMojangMappings())
    modImplementation("net.fabricmc:fabric-loader:${property("fabric_loader_version")}")
    modImplementation("net.fabricmc.fabric-api:fabric-api:${property("fabric_api_version")}")
    modImplementation("net.fabricmc:fabric-language-kotlin:${property("fabric_kotlin_version")}")
    modImplementation("maven.modrinth:cobblemon:${property("cobblemon_artifact_version")}")

    testImplementation(kotlin("test"))
    testImplementation("org.junit.jupiter:junit-jupiter:5.11.4")
    testImplementation("org.ow2.asm:asm-tree:9.7.1")
    testRuntimeOnly("org.junit.platform:junit-platform-launcher")
}

java {
    toolchain.languageVersion.set(JavaLanguageVersion.of(21))
    withSourcesJar()
}
kotlin { jvmToolchain(21) }
tasks.withType<JavaCompile>().configureEach { options.release.set(21) }

tasks.processResources {
    val metadata = mapOf("version" to project.version, "cobblemon_version" to project.property("cobblemon_version"))
    inputs.properties(metadata)
    filesMatching("fabric.mod.json") { expand(metadata) }
}

tasks.test {
    useJUnitPlatform()
    inputs.file("README.md")
}

tasks.jar {
    from("THIRD_PARTY_NOTICES.md")
    from("LICENSES") { into("LICENSES") }
}

tasks.wrapper {
    gradleVersion = "8.12"
    distributionType = Wrapper.DistributionType.BIN
    distributionSha256Sum = "7a00d51fb93147819aab76024feece20b6b84e420694101f276be952e08bef03"
}
