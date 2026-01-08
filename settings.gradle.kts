plugins {
    id("org.gradle.toolchains.foojay-resolver-convention") version "0.8.0"
}

rootProject.name = "phantasmal-world"

include(
    ":cli-exporter",
    ":core",
    ":psolib",
    ":cell",
    ":psoserv",
    ":test-utils",
    ":web",
    ":web:assembly-worker",
    ":web:assets-generation",
    ":web:shared",
    ":webui",
)
