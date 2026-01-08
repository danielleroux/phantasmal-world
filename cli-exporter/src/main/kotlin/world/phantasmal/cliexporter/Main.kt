package world.phantasmal.cliexporter

import com.github.ajalt.clikt.core.CliktCommand
import com.github.ajalt.clikt.parameters.arguments.argument
import com.github.ajalt.clikt.parameters.types.path
import world.phantasmal.core.Failure
import world.phantasmal.core.Success
import world.phantasmal.psolib.buffer.Buffer
import world.phantasmal.psolib.cursor.cursor
import world.phantasmal.psolib.fileFormats.ninja.parseNj
import world.phantasmal.psolib.fileFormats.ninja.parseNjm
import world.phantasmal.psolib.fileFormats.ninja.parseXj
import java.nio.file.Path
import kotlin.io.path.exists
import kotlin.io.path.extension
import kotlin.io.path.readBytes
import kotlin.system.exitProcess

class ExportCommand : CliktCommand(
    name = "cli-exporter",
    help = "Export PSO animation files (NJM format) to FBX format"
) {
    private val geometryFile by argument(
        name = "GEOMETRY",
        help = "Input NJ/XJ geometry file (for skeleton structure)"
    ).path(mustExist = true, canBeDir = false, mustBeReadable = true)

    private val animationFile by argument(
        name = "ANIMATION",
        help = "Input NJM animation file"
    ).path(mustExist = true, canBeDir = false, mustBeReadable = true)

    private val outputFile by argument(
        name = "OUTPUT",
        help = "Output FBX file path"
    ).path(canBeDir = false)

    override fun run() {
        try {
            echo("Loading geometry file: $geometryFile")
            val geometryObjects = loadGeometry(geometryFile)

            echo("Loading animation file: $animationFile")
            val animation = loadAnimation(animationFile)

            echo("Exporting to FBX: $outputFile")
            val exporter = FbxExporter()
            val fbxContent = exporter.export(geometryObjects, animation)

            outputFile.toFile().writeText(fbxContent)

            echo("✓ Successfully exported animation to $outputFile")
            echo("  Frame count: ${animation.frameCount}")
            echo("  Bone count: ${geometryObjects.firstOrNull()?.boneCount() ?: 0}")
            echo("  Animation tracks: ${animation.motionData.size}")
        } catch (e: Exception) {
            echo("Error: ${e.message}", err = true)
            e.printStackTrace()
            exitProcess(1)
        }
    }

    private fun loadGeometry(path: Path): List<world.phantasmal.psolib.fileFormats.ninja.NinjaObject<*, *>> {
        if (!path.exists()) {
            throw IllegalArgumentException("Geometry file not found: $path")
        }

        val bytes = path.readBytes()
        val cursor = Buffer.fromByteArray(bytes).cursor()
        val extension = path.extension.lowercase()

        return when (extension) {
            "nj" -> {
                when (val result = parseNj(cursor)) {
                    is Success -> result.value
                    is Failure -> throw IllegalArgumentException("Failed to parse NJ file: ${result.problems.joinToString()}")
                }
            }
            "xj" -> {
                when (val result = parseXj(cursor)) {
                    is Success -> result.value
                    is Failure -> throw IllegalArgumentException("Failed to parse XJ file: ${result.problems.joinToString()}")
                }
            }
            else -> throw IllegalArgumentException("Unsupported geometry file format: $extension (expected .nj or .xj)")
        }
    }

    private fun loadAnimation(path: Path): world.phantasmal.psolib.fileFormats.ninja.NjMotion {
        if (!path.exists()) {
            throw IllegalArgumentException("Animation file not found: $path")
        }

        val bytes = path.readBytes()
        val cursor = Buffer.fromByteArray(bytes).cursor()

        return try {
            parseNjm(cursor)
        } catch (e: Exception) {
            throw IllegalArgumentException("Failed to parse NJM file: ${e.message}", e)
        }
    }
}

fun main(args: Array<String>) = ExportCommand().main(args)
