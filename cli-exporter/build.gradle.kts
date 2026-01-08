plugins {
    id("world.phantasmal.jvm")
    application
}

application {
    mainClass.set("world.phantasmal.cliexporter.MainKt")
}

dependencies {
    implementation(project(":core"))
    implementation(project(":psolib"))
    
    // For CLI argument parsing
    implementation("com.github.ajalt.clikt:clikt:3.5.0")
    
    // For glTF data structures
    implementation("de.javagl:jgltf-impl-v2:2.0.3")
    
    // For JSON serialization
    implementation("com.fasterxml.jackson.core:jackson-databind:2.15.4")
}

tasks.jar {
    manifest {
        attributes["Main-Class"] = "world.phantasmal.cliexporter.MainKt"
    }
    
    // Create fat JAR with dependencies
    from(configurations.runtimeClasspath.get().map { if (it.isDirectory) it else zipTree(it) })
    duplicatesStrategy = DuplicatesStrategy.EXCLUDE
}
