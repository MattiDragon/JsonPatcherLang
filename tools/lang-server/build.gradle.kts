plugins {
    id("com.github.johnrengelman.shadow") version "8.1.1"
}

dependencies {
    implementation(rootProject)
    implementation(project(":doctool"))
    implementation("org.eclipse.lsp4j:org.eclipse.lsp4j:0.23.1")
}

tasks.jar {
    manifest.attributes["Main-Class"] = "io.github.mattidragon.jsonpatcher.server.LangServerMain"
}

tasks.assemble {
    dependsOn("shadowJar")
}

tasks.shadowJar {
    exclude("META-INF/maven/**")
    exclude("about*.html")
}