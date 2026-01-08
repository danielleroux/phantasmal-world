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
    implementation("com.github.ajalt.clikt:clikt:3.5.0")
}

tasks.jar {
    manifest {
        attributes["Main-Class"] = "world.phantasmal.cliexporter.MainKt"
    }
    
    // Create fat JAR with dependencies
    from(configurations.runtimeClasspath.get().map { if (it.isDirectory) it else zipTree(it) })
    duplicatesStrategy = DuplicatesStrategy.EXCLUDE
}
