plugins {
    id("shared")
}

dependencies {
    implementation(project(":analysis"))
    implementation(project(":parser"))
    api(project(":shared-runtime"))
    implementation(libs.bundles.asm)
}