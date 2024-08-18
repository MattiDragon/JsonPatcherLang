plugins {
    id("shared")
    id("java-test-fixtures")
}

dependencies { 
    testFixturesImplementation("org.junit.jupiter:junit-jupiter:5.10.0")
    testFixturesApi(project(":parser"))
}