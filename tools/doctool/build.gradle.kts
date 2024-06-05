plugins {
    id("com.github.johnrengelman.shadow") version "8.1.1"
}

dependencies {
    api(rootProject)
    api("org.commonmark:commonmark:0.22.0")
    api("org.commonmark:commonmark-ext-gfm-tables:0.22.0")
    api("org.commonmark:commonmark-ext-gfm-strikethrough:0.22.0")
}

tasks.jar {
    manifest.attributes["Main-Class"] = "io.github.mattidragon.jsonpatcher.docs.DocTool"
}

tasks.assemble {
    dependsOn("shadowJar")
}

tasks.shadowJar {
    exclude("META-INF/maven/org.commonmark/**")
    from(layout.projectDirectory.dir("src/main/dist_resources"))
}