plugins {
    id("shared")
}

tasks.processResources {
    exclude("jsonpatcher-workspace.json")
}