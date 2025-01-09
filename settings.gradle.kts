rootProject.name = "JsonPatcherLang"

fun module(name: String) {
    include(":$name")
    project(":$name").projectDir = file("modules/$name")
}

module("shared-runtime")
module("benchmark")
module("parser")
module("legacy-runtime")
module("bytecode-runtime")
module("ast")
module("analysis")

fun tool(name: String) {
    include(":$name")
    project(":$name").projectDir = file("tools/$name")
}

tool("doctool")
tool("lang-server")
tool("formatter")
