plugins {
    `java-library`
    `maven-publish`
}

project.version = project.property("version")!!
project.group = project.property("maven_group")!!
base.archivesName.set(project.property("archives_base_name") as String)

repositories {
    mavenCentral()
}

dependencies {
    implementation("org.jetbrains:annotations:24.0.1")

    // Use junit
    testImplementation("org.junit.jupiter:junit-jupiter:5.10.0")
    testRuntimeOnly("org.junit.platform:junit-platform-launcher")
}

tasks.test {
    useJUnitPlatform()
}

tasks.processResources {
    inputs.property("version", project.version)
    filteringCharset = "UTF-8"

    filesMatching("fabric.mod.json") {
        expand(mapOf("version" to project.version))
    }
}

tasks.withType<JavaCompile>().configureEach {
    options.release.set(21)
}

java {
    withSourcesJar()
}

tasks.jar {
    from("LICENSE") {
        rename { "${it}_${base.archivesName.get()}"}
    }

    manifest.attributes(mapOf("Fabric-Loom-Remap" to "false"))
}

publishing {
    publications.create<MavenPublication>("mavenJava") {
        from(components["java"])
        artifactId = base.archivesName.get()
    }
    repositories {}
}
