plugins {
    alias(libs.plugins.shadow)
    id("shared")
}

dependencies {
    implementation(project(":stdlib"))
    implementation(project(":formatter"))
    implementation(project(":tool-common"))
    implementation(libs.lsp4j)
}

tasks.jar {
    manifest.attributes["Main-Class"] = "dev.mattidragon.jsonpatcher.server.LangServerMain"
}

tasks.assemble {
    dependsOn("shadowJar")
}

tasks.shadowJar {
    exclude("META-INF/maven/**")
    exclude("META-INF/LICENSE.txt")
    exclude("META-INF/proguard/*")
    exclude("META-INF/versions/9/module-info.class")
    exclude("about*.html")
    exclude("fabric.mod.json")
}