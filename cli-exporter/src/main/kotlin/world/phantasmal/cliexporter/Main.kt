package world.phantasmal.cliexporter

import com.github.ajalt.clikt.core.CliktCommand
import com.github.ajalt.clikt.parameters.arguments.argument
import com.github.ajalt.clikt.parameters.types.file
import world.phantasmal.core.Failure
import world.phantasmal.core.Success
import world.phantasmal.psolib.buffer.Buffer
import world.phantasmal.psolib.cursor.cursor
import world.phantasmal.psolib.fileFormats.ninja.parseNj
import world.phantasmal.psolib.fileFormats.ninja.parseNjm
import world.phantasmal.psolib.fileFormats.ninja.parseXj
import java.io.File
import kotlin.system.exitProcess

class AnimationExporterCommand : CliktCommand(
    name = "pso-animation-exporter",
    help = """
        Export Phantasy Star Online animation files to FBX or glTF formats.
        
        The output format is automatically detected from the file extension:
        - .fbx  -> FBX ASCII format
        - .gltf -> glTF 2.0 JSON format
        - .glb  -> glTF 2.0 binary format
    """.trimIndent()
) {
    private val geometryFile by argument(
        help = "Path to the geometry file (NJ or XJ format)"
    ).file(mustExist = true, canBeDir = false, mustBeReadable = true)
    
    private val animationFile by argument(
        help = "Path to the animation file (NJM format)"
    ).file(mustExist = true, canBeDir = false, mustBeReadable = true)
    
    private val outputFile by argument(
        help = "Output file path (.fbx, .gltf, or .glb)"
    ).file(canBeDir = false)
    
    override fun run() {
        echo("Parsing geometry file: ${geometryFile.name}")
        
        // Parse geometry
        val ninjaObjects = try {
            val cursor = Buffer.fromByteArray(geometryFile.readBytes()).cursor()
            val result = when (geometryFile.extension.lowercase()) {
                "nj" -> parseNj(cursor)
                "xj" -> parseXj(cursor)
                else -> {
                    echo("Error: Geometry file must have .nj or .xj extension", err = true)
                    exitProcess(1)
                }
            }
            
            when (result) {
                is Success -> result.value
                is Failure -> {
                    echo("Error parsing geometry file: ${result.problems.joinToString(", ")}", err = true)
                    exitProcess(1)
                }
            }
        } catch (e: Exception) {
            echo("Error reading geometry file: ${e.message}", err = true)
            exitProcess(1)
        }
        
        if (ninjaObjects.isEmpty()) {
            echo("Error: No objects found in geometry file", err = true)
            exitProcess(1)
        }
        
        echo("Found ${ninjaObjects.first().boneCount()} bones in skeleton")
        
        // Parse animation
        echo("Parsing animation file: ${animationFile.name}")
        val motion = try {
            val cursor = Buffer.fromByteArray(animationFile.readBytes()).cursor()
            parseNjm(cursor)
        } catch (e: Exception) {
            echo("Error parsing animation file: ${e.message}", err = true)
            exitProcess(1)
        }
        
        echo("Animation: ${motion.frameCount} frames, ${motion.motionData.size} bone tracks")
        
        // Detect output format from extension
        val outputFormat = when (outputFile.extension.lowercase()) {
            "fbx" -> OutputFormat.FBX
            "gltf" -> OutputFormat.GLTF_JSON
            "glb" -> OutputFormat.GLTF_BINARY
            else -> {
                echo("Error: Output file must have .fbx, .gltf, or .glb extension", err = true)
                exitProcess(1)
            }
        }
        
        echo("Exporting to ${outputFormat.name} format: ${outputFile.name}")
        
        try {
            when (outputFormat) {
                OutputFormat.FBX -> {
                    FbxExporter.export(ninjaObjects.first(), motion, outputFile)
                }
                OutputFormat.GLTF_JSON, OutputFormat.GLTF_BINARY -> {
                    GltfExporter.export(
                        ninjaObjects.first(),
                        motion,
                        outputFile,
                        binary = outputFormat == OutputFormat.GLTF_BINARY
                    )
                }
            }
            
            echo("Successfully exported to: ${outputFile.absolutePath}")
        } catch (e: Exception) {
            echo("Error during export: ${e.message}", err = true)
            e.printStackTrace()
            exitProcess(1)
        }
    }
}

enum class OutputFormat {
    FBX,
    GLTF_JSON,
    GLTF_BINARY
}

fun main(args: Array<String>) = AnimationExporterCommand().main(args)
