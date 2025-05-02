plugins {
    id("com.github.johnrengelman.shadow") version "8.1.1"
    id("shared")
    id("application")
}

dependencies {
    implementation(project(":doctool"))
    implementation(project(":formatter"))
    implementation(libs.picocli)
    annotationProcessor(libs.picocli.codegen)
}

val genVersionFile: Task by tasks.creating {
    val outputFile = project.layout.buildDirectory.file("generated-files/version")
    outputs.file(outputFile)
    doLast {
        outputFile.get().asFile.writeText(version.toString())
    }
}

tasks.compileJava {
    options.compilerArgs.addAll(listOf("-Aproject=${project.group}/${project.name}", "-parameters"))
}

tasks.processResources {
    dependsOn(genVersionFile)
    from(genVersionFile.outputs)
}

application {
    mainClass = "dev.mattidragon.jsonpatcher.cli.Main"
    mainModule = "jsonpatcher.tools.cli"
}

tasks.assemble {
    dependsOn("shadowJar")
}