plugins {
    id("shared")
}

dependencies {
    api(project(":parser"))
    api(libs.bundles.commonmark)
    testImplementation(testFixtures(project(":runtime")))
}
