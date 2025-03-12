plugins {
    id("com.github.johnrengelman.shadow") version "8.1.1"
    id("shared")
}

dependencies {
    implementation(project(":runtime"))
    implementation(project(":parser"))
    testImplementation(testFixtures(project(":runtime")))
}

tasks.jar {
    manifest.attributes["Main-Class"] = "dev.mattidragon.jsonpatcher.formatter.Main"
}

tasks.assemble {
    dependsOn("shadowJar")
}