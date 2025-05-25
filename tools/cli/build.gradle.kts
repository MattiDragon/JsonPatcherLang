plugins {
    alias(libs.plugins.shadow)
    id("shared")
    id("application")
}

dependencies {
    implementation(project(":formatter"))
    implementation(project(":stdlib"))
    implementation(project(":tool-common"))
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
    applicationName = "jsonpatcher-cli"
    mainClass = "dev.mattidragon.jsonpatcher.cli.Main"
    mainModule = "jsonpatcher.tools.cli"
}

tasks.assemble {
    dependsOn("shadowJar")
}

distributions.named("shadow") {
    distributionBaseName = application.applicationName + "-shadow"
}