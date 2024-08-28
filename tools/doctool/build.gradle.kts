plugins {
    id("com.github.johnrengelman.shadow") version "8.1.1"
    id("shared")
}

dependencies {
    api(project(":parser"))
    api(libs.bundles.commonmark)
}

tasks.jar {
    manifest.attributes["Main-Class"] = "io.github.mattidragon.jsonpatcher.docs.DocTool"
}

tasks.assemble {
    dependsOn("shadowJar")
}

tasks.shadowJar {
    exclude("META-INF/maven/org.commonmark/**")
}