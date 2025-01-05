plugins {
    id("shared")
    id("java-test-fixtures")
}

dependencies { 
    testFixturesImplementation(libs.junit.jupiter)
    testFixturesImplementation(libs.jspecify)
    testFixturesApi(project(":parser"))
    testFixturesApi(project(":legacy-runtime"))
}