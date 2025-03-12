plugins {
    id("shared")
    id("java-test-fixtures")
}

dependencies {
    implementation(project(":compiler"))
    implementation(project(":parser"))

    testFixturesImplementation(libs.junit.jupiter)
    testFixturesImplementation(libs.jspecify)
    testFixturesApi(project(":parser"))
    testFixturesApi(project(":analysis"))
    //testFixturesApi(project(":runtime"))
}
