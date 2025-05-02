rootProject.name = "JsonPatcherLang"

fun module(name: String) {
    include(":$name")
    project(":$name").projectDir = file("modules/$name")
}

module("benchmark")
module("parser")
module("compiler")
module("runtime")
module("ast")
module("analysis")
module("stdlib")

fun tool(name: String) {
    include(":$name")
    project(":$name").projectDir = file("tools/$name")
}

tool("cli")
tool("doctool")
tool("lang-server")
tool("formatter")
