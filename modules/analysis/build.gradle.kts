plugins {
    id("shared")
}

dependencies {
    api(project(":ast"))
    api(project(":parser"))
    testImplementation(testFixtures(project(":runtime")))
}
