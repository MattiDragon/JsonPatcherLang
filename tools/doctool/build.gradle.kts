plugins {
    id("com.github.johnrengelman.shadow") version "8.1.1"
}

dependencies {
    implementation(rootProject)
    implementation("org.commonmark:commonmark:0.22.0")
    implementation("org.commonmark:commonmark-ext-gfm-tables:0.22.0")
    implementation("org.commonmark:commonmark-ext-gfm-strikethrough:0.22.0")
}

tasks.jar {
    manifest.attributes["Main-Class"] = "io.github.mattidragon.jsonpatcher.docs.DocTool"
}

tasks.shadowJar {
    exclude("META-INF/maven/org.commonmark/**")
}