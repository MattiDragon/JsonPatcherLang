plugins {
    id("com.github.johnrengelman.shadow") version "8.1.1"
    id("shared")
}

dependencies {
    implementation(project(":shared-runtime"))
    implementation(project(":parser"))
    testImplementation(testFixtures(project(":shared-runtime")))
}

tasks.jar {
    manifest.attributes["Main-Class"] = "dev.mattidragon.jsonpatcher.formatter.Main"
}

tasks.assemble {
    dependsOn("shadowJar")
}