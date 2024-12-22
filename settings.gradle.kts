rootProject.name = "JsonPatcherLang"

include(":shared")
project(":shared").projectDir = file("modules/shared")
include(":benchmark")
project(":benchmark").projectDir = file("modules/benchmark")
include(":parser")
project(":parser").projectDir = file("modules/parser")
include(":legacy-runtime")
project(":legacy-runtime").projectDir = file("modules/legacy-runtime")
include(":bytecode-runtime")
project(":bytecode-runtime").projectDir = file("modules/bytecode-runtime")

include(":doctool")
project(":doctool").projectDir = file("tools/doctool")
include(":lang-server")
project(":lang-server").projectDir = file("tools/lang-server")