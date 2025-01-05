plugins {
    `java-library`
    `maven-publish`
}

version = rootProject.version
group = rootProject.group
base.archivesName = project.property("archives_base_name") as String

repositories {
    mavenCentral()
}

dependencies {
    val libs = versionCatalogs.named("libs")
    
    compileOnly(libs.findLibrary("annotations").orElseThrow())
    implementation(libs.findLibrary("jspecify").orElseThrow())

    // Use junit
    testImplementation(libs.findLibrary("junit-jupiter").orElseThrow())
    testRuntimeOnly(libs.findLibrary("junit-platform").orElseThrow())
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