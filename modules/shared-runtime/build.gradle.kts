plugins {
    id("shared")
    id("java-test-fixtures")
}

dependencies {
    api(project(":ast"))
    testFixturesImplementation(libs.junit.jupiter)
    testFixturesImplementation(libs.jspecify)
    testFixturesApi(project(":parser"))
    testFixturesApi(project(":analysis"))
}