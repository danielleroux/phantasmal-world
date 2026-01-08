package world.phantasmal.cliexporter

import de.javagl.jgltf.impl.v2.*
import world.phantasmal.psolib.fileFormats.ninja.*
import java.io.File
import java.io.FileOutputStream
import java.nio.ByteBuffer
import java.nio.ByteOrder
import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.databind.SerializationFeature
import kotlin.math.cos
import kotlin.math.sin

/**
 * Exports PSO animations to glTF 2.0 format.
 * PSO uses 30 FPS frame rate.
 */
object GltfExporter {
    private const val PSO_FRAME_RATE = 30.0f
    
    fun export(
        ninjaObject: NinjaObject<*, *>,
        motion: NjMotion,
        outputFile: File,
        binary: Boolean
    ) {
        val gltf = GlTF()
        gltf.asset = Asset().apply {
            version = "2.0"
            generator = "Phantasmal World CLI Exporter"
        }
        
        val buffers = mutableListOf<Buffer>()
        val bufferViews = mutableListOf<BufferView>()
        val accessors = mutableListOf<Accessor>()
        val nodes = mutableListOf<Node>()
        val animations = mutableListOf<Animation>()
        
        // Collect binary data
        val bufferData = mutableListOf<ByteArray>()
        
        // Build skeleton hierarchy
        val boneCount = ninjaObject.boneCount()
        val boneIndices = mutableMapOf<NinjaObject<*, *>, Int>()
        
        buildNodeHierarchy(ninjaObject, nodes, boneIndices, -1)
        
        // Create animation
        if (motion.motionData.isNotEmpty()) {
            val animation = Animation()
            animation.name = "Take 001"
            
            val channels = mutableListOf<AnimationChannel>()
            val samplers = mutableListOf<AnimationSampler>()
            
            // Create animation data for each bone
            for ((boneIndex, motionData) in motion.motionData.withIndex()) {
                if (boneIndex >= boneCount) {
                    break // More animation tracks than bones
                }
                
                val bone = ninjaObject.getBone(boneIndex) ?: continue
                val nodeIndex = boneIndices[bone] ?: continue
                
                for (track in motionData.tracks) {
                    when (track) {
                        is NjKeyframeTrack.Position -> {
                            addAnimationTrack(
                                track.keyframes,
                                nodeIndex,
                                "translation",
                                motion.frameCount,
                                bufferData,
                                bufferViews,
                                accessors,
                                channels,
                                samplers,
                                motion.interpolation,
                                convertVector = true
                            )
                        }
                        is NjKeyframeTrack.EulerAngles -> {
                            addRotationTrack(
                                track.keyframes,
                                nodeIndex,
                                motion.frameCount,
                                bufferData,
                                bufferViews,
                                accessors,
                                channels,
                                samplers,
                                motion.interpolation
                            )
                        }
                        is NjKeyframeTrack.Scale -> {
                            addAnimationTrack(
                                track.keyframes,
                                nodeIndex,
                                "scale",
                                motion.frameCount,
                                bufferData,
                                bufferViews,
                                accessors,
                                channels,
                                samplers,
                                motion.interpolation,
                                convertVector = true
                            )
                        }
                        is NjKeyframeTrack.Quaternion -> {
                            addQuaternionTrack(
                                track.keyframes,
                                nodeIndex,
                                motion.frameCount,
                                bufferData,
                                bufferViews,
                                accessors,
                                channels,
                                samplers,
                                motion.interpolation
                            )
                        }
                    }
                }
            }
            
            animation.channels = channels
            animation.samplers = samplers
            animations.add(animation)
        }
        
        // Combine all buffer data into a single buffer
        if (bufferData.isNotEmpty()) {
            val totalSize = bufferData.sumOf { it.size }
            val combinedBuffer = ByteArray(totalSize)
            var offset = 0
            for (data in bufferData) {
                data.copyInto(combinedBuffer, offset)
                offset += data.size
            }
            
            val buffer = Buffer()
            buffer.byteLength = totalSize
            buffers.add(buffer)
            
            // Update buffer views to reference the single buffer
            bufferViews.forEach { it.buffer = 0 }
            
            // Write output
            if (binary) {
                writeGlb(gltf, nodes, buffers, bufferViews, accessors, animations, combinedBuffer, outputFile)
            } else {
                writeGltf(gltf, nodes, buffers, bufferViews, accessors, animations, combinedBuffer, outputFile)
            }
        } else {
            // No animation data - just write the structure
            gltf.buffers = emptyList()
            gltf.bufferViews = emptyList()
            gltf.accessors = emptyList()
            gltf.nodes = nodes
            gltf.animations = emptyList()
            
            // Create scene
            val scene = Scene()
            scene.nodes = listOf(0) // Root node
            gltf.scenes = listOf(scene)
            gltf.scene = 0
            
            writeGltfJson(gltf, outputFile)
        }
    }
    
    private fun writeGltf(
        gltf: GlTF,
        nodes: List<Node>,
        buffers: List<Buffer>,
        bufferViews: List<BufferView>,
        accessors: List<Accessor>,
        animations: List<Animation>,
        bufferData: ByteArray,
        outputFile: File
    ) {
        // Update buffer to reference external .bin file
        val binFileName = outputFile.nameWithoutExtension + ".bin"
        buffers[0].uri = binFileName
        
        gltf.buffers = buffers
        gltf.bufferViews = bufferViews
        gltf.accessors = accessors
        gltf.nodes = nodes
        gltf.animations = animations
        
        // Create scene
        val scene = Scene()
        scene.nodes = listOf(0) // Root node
        gltf.scenes = listOf(scene)
        gltf.scene = 0
        
        // Write JSON
        writeGltfJson(gltf, outputFile)
        
        // Write binary file
        val binFile = File(outputFile.parent, binFileName)
        FileOutputStream(binFile).use { it.write(bufferData) }
    }
    
    private fun writeGlb(
        gltf: GlTF,
        nodes: List<Node>,
        buffers: List<Buffer>,
        bufferViews: List<BufferView>,
        accessors: List<Accessor>,
        animations: List<Animation>,
        bufferData: ByteArray,
        outputFile: File
    ) {
        gltf.buffers = buffers
        gltf.bufferViews = bufferViews
        gltf.accessors = accessors
        gltf.nodes = nodes
        gltf.animations = animations
        
        // Create scene
        val scene = Scene()
        scene.nodes = listOf(0) // Root node
        gltf.scenes = listOf(scene)
        gltf.scene = 0
        
        // Serialize JSON
        val mapper = ObjectMapper()
        val jsonBytes = mapper.writeValueAsBytes(gltf)
        
        // Pad JSON to 4-byte alignment
        val jsonPadding = (4 - (jsonBytes.size % 4)) % 4
        val paddedJsonSize = jsonBytes.size + jsonPadding
        
        // Pad buffer data to 4-byte alignment
        val bufferPadding = (4 - (bufferData.size % 4)) % 4
        val paddedBufferSize = bufferData.size + bufferPadding
        
        // Write GLB file
        FileOutputStream(outputFile).use { out ->
            // Header
            val header = ByteBuffer.allocate(12).order(ByteOrder.LITTLE_ENDIAN)
            header.putInt(0x46546C67) // magic "glTF"
            header.putInt(2) // version
            header.putInt(12 + 8 + paddedJsonSize + 8 + paddedBufferSize) // total length
            out.write(header.array())
            
            // JSON chunk
            val jsonChunkHeader = ByteBuffer.allocate(8).order(ByteOrder.LITTLE_ENDIAN)
            jsonChunkHeader.putInt(paddedJsonSize)
            jsonChunkHeader.putInt(0x4E4F534A) // chunk type "JSON"
            out.write(jsonChunkHeader.array())
            out.write(jsonBytes)
            repeat(jsonPadding) { out.write(0x20) } // Space padding
            
            // Binary chunk
            val binChunkHeader = ByteBuffer.allocate(8).order(ByteOrder.LITTLE_ENDIAN)
            binChunkHeader.putInt(paddedBufferSize)
            binChunkHeader.putInt(0x004E4942) // chunk type "BIN\0"
            out.write(binChunkHeader.array())
            out.write(bufferData)
            repeat(bufferPadding) { out.write(0x00) } // Zero padding
        }
    }
    
    private fun writeGltfJson(gltf: GlTF, outputFile: File) {
        val mapper = ObjectMapper()
        mapper.enable(SerializationFeature.INDENT_OUTPUT)
        mapper.writeValue(outputFile, gltf)
    }
    
    private fun buildNodeHierarchy(
        obj: NinjaObject<*, *>,
        nodes: MutableList<Node>,
        boneIndices: MutableMap<NinjaObject<*, *>, Int>,
        parentIndex: Int
    ): Int {
        if (obj.evaluationFlags.skip) {
            // Process children but don't create a node for this object
            for (child in obj.children) {
                buildNodeHierarchy(child, nodes, boneIndices, parentIndex)
            }
            return parentIndex
        }
        
        val nodeIndex = nodes.size
        boneIndices[obj] = nodeIndex
        
        val node = Node()
        node.name = "Bone_$nodeIndex"
        
        // Set transform
        if (!obj.evaluationFlags.noTranslate) {
            node.translation = floatArrayOf(
                obj.position.x,
                obj.position.y,
                obj.position.z
            )
        }
        
        if (!obj.evaluationFlags.noRotate) {
            // Convert Euler angles to quaternion
            val quat = eulerToQuaternion(obj.rotation.x, obj.rotation.y, obj.rotation.z)
            node.rotation = floatArrayOf(quat[0], quat[1], quat[2], quat[3])
        }
        
        if (!obj.evaluationFlags.noScale) {
            node.scale = floatArrayOf(
                obj.scale.x,
                obj.scale.y,
                obj.scale.z
            )
        }
        
        nodes.add(node)
        
        // Process children
        if (!obj.evaluationFlags.breakChildTrace) {
            val childIndices = mutableListOf<Int>()
            for (child in obj.children) {
                val childIndex = buildNodeHierarchy(child, nodes, boneIndices, nodeIndex)
                if (childIndex != nodeIndex) {
                    childIndices.add(childIndex)
                }
            }
            if (childIndices.isNotEmpty()) {
                node.children = childIndices.toList()
            }
        }
        
        return nodeIndex
    }
    
    private fun addAnimationTrack(
        keyframes: List<NjKeyframe.Vector>,
        nodeIndex: Int,
        path: String,
        frameCount: Int,
        bufferData: MutableList<ByteArray>,
        bufferViews: MutableList<BufferView>,
        accessors: MutableList<Accessor>,
        channels: MutableList<AnimationChannel>,
        samplers: MutableList<AnimationSampler>,
        interpolation: NjInterpolation,
        convertVector: Boolean
    ) {
        if (keyframes.isEmpty()) return
        
        val samplerIndex = samplers.size
        
        // Create time accessor
        val timeData = createTimeData(keyframes)
        val timeAccessorIndex = createAccessor(
            timeData,
            "SCALAR",
            bufferData,
            bufferViews,
            accessors
        )
        
        // Create value accessor
        val valueData = createVectorData(keyframes, convertVector)
        val valueAccessorIndex = createAccessor(
            valueData,
            "VEC3",
            bufferData,
            bufferViews,
            accessors
        )
        
        // Create sampler
        val sampler = AnimationSampler()
        sampler.input = timeAccessorIndex
        sampler.output = valueAccessorIndex
        sampler.interpolation = when (interpolation) {
            NjInterpolation.Linear -> "LINEAR"
            NjInterpolation.Spline -> "CUBICSPLINE"
            NjInterpolation.UserFunction -> "LINEAR"
        }
        samplers.add(sampler)
        
        // Create channel
        val channel = AnimationChannel()
        channel.sampler = samplerIndex
        channel.target = AnimationChannelTarget().apply {
            node = nodeIndex
            this.path = path
        }
        channels.add(channel)
    }
    
    private fun addRotationTrack(
        keyframes: List<NjKeyframe.Vector>,
        nodeIndex: Int,
        frameCount: Int,
        bufferData: MutableList<ByteArray>,
        bufferViews: MutableList<BufferView>,
        accessors: MutableList<Accessor>,
        channels: MutableList<AnimationChannel>,
        samplers: MutableList<AnimationSampler>,
        interpolation: NjInterpolation
    ) {
        if (keyframes.isEmpty()) return
        
        val samplerIndex = samplers.size
        
        // Create time accessor
        val timeData = createTimeData(keyframes)
        val timeAccessorIndex = createAccessor(
            timeData,
            "SCALAR",
            bufferData,
            bufferViews,
            accessors
        )
        
        // Create quaternion value accessor
        val valueData = createQuaternionDataFromEuler(keyframes)
        val valueAccessorIndex = createAccessor(
            valueData,
            "VEC4",
            bufferData,
            bufferViews,
            accessors
        )
        
        // Create sampler
        val sampler = AnimationSampler()
        sampler.input = timeAccessorIndex
        sampler.output = valueAccessorIndex
        sampler.interpolation = when (interpolation) {
            NjInterpolation.Linear -> "LINEAR"
            NjInterpolation.Spline -> "CUBICSPLINE"
            NjInterpolation.UserFunction -> "LINEAR"
        }
        samplers.add(sampler)
        
        // Create channel
        val channel = AnimationChannel()
        channel.sampler = samplerIndex
        channel.target = AnimationChannelTarget().apply {
            node = nodeIndex
            path = "rotation"
        }
        channels.add(channel)
    }
    
    private fun addQuaternionTrack(
        keyframes: List<NjKeyframe.Quaternion>,
        nodeIndex: Int,
        frameCount: Int,
        bufferData: MutableList<ByteArray>,
        bufferViews: MutableList<BufferView>,
        accessors: MutableList<Accessor>,
        channels: MutableList<AnimationChannel>,
        samplers: MutableList<AnimationSampler>,
        interpolation: NjInterpolation
    ) {
        if (keyframes.isEmpty()) return
        
        val samplerIndex = samplers.size
        
        // Create time accessor
        val timeData = ByteBuffer.allocate(keyframes.size * 4).order(ByteOrder.LITTLE_ENDIAN)
        for (keyframe in keyframes) {
            timeData.putFloat(keyframe.frame / PSO_FRAME_RATE)
        }
        val timeAccessorIndex = createAccessor(
            timeData.array(),
            "SCALAR",
            bufferData,
            bufferViews,
            accessors
        )
        
        // Create quaternion value accessor (x, y, z, w format for glTF)
        val valueData = ByteBuffer.allocate(keyframes.size * 16).order(ByteOrder.LITTLE_ENDIAN)
        for (keyframe in keyframes) {
            valueData.putFloat(keyframe.imaginary.x)
            valueData.putFloat(keyframe.imaginary.y)
            valueData.putFloat(keyframe.imaginary.z)
            valueData.putFloat(keyframe.real)
        }
        val valueAccessorIndex = createAccessor(
            valueData.array(),
            "VEC4",
            bufferData,
            bufferViews,
            accessors
        )
        
        // Create sampler
        val sampler = AnimationSampler()
        sampler.input = timeAccessorIndex
        sampler.output = valueAccessorIndex
        sampler.interpolation = when (interpolation) {
            NjInterpolation.Linear -> "LINEAR"
            NjInterpolation.Spline -> "CUBICSPLINE"
            NjInterpolation.UserFunction -> "LINEAR"
        }
        samplers.add(sampler)
        
        // Create channel
        val channel = AnimationChannel()
        channel.sampler = samplerIndex
        channel.target = AnimationChannelTarget().apply {
            node = nodeIndex
            path = "rotation"
        }
        channels.add(channel)
    }
    
    private fun createTimeData(keyframes: List<NjKeyframe>): ByteArray {
        val buffer = ByteBuffer.allocate(keyframes.size * 4).order(ByteOrder.LITTLE_ENDIAN)
        for (keyframe in keyframes) {
            buffer.putFloat(keyframe.frame / PSO_FRAME_RATE)
        }
        return buffer.array()
    }
    
    private fun createVectorData(keyframes: List<NjKeyframe.Vector>, convertVector: Boolean): ByteArray {
        val buffer = ByteBuffer.allocate(keyframes.size * 12).order(ByteOrder.LITTLE_ENDIAN)
        for (keyframe in keyframes) {
            buffer.putFloat(keyframe.value.x)
            buffer.putFloat(keyframe.value.y)
            buffer.putFloat(keyframe.value.z)
        }
        return buffer.array()
    }
    
    private fun createQuaternionDataFromEuler(keyframes: List<NjKeyframe.Vector>): ByteArray {
        val buffer = ByteBuffer.allocate(keyframes.size * 16).order(ByteOrder.LITTLE_ENDIAN)
        for (keyframe in keyframes) {
            val quat = eulerToQuaternion(keyframe.value.x, keyframe.value.y, keyframe.value.z)
            buffer.putFloat(quat[0]) // x
            buffer.putFloat(quat[1]) // y
            buffer.putFloat(quat[2]) // z
            buffer.putFloat(quat[3]) // w
        }
        return buffer.array()
    }
    
    private fun createAccessor(
        data: ByteArray,
        type: String,
        bufferData: MutableList<ByteArray>,
        bufferViews: MutableList<BufferView>,
        accessors: MutableList<Accessor>
    ): Int {
        val bufferViewIndex = bufferViews.size
        val accessorIndex = accessors.size
        
        // Calculate current buffer offset
        val byteOffset = bufferData.sumOf { it.size }
        
        val bufferView = BufferView()
        bufferView.buffer = 0
        bufferView.byteOffset = byteOffset
        bufferView.byteLength = data.size
        bufferViews.add(bufferView)
        
        bufferData.add(data)
        
        val accessor = Accessor()
        accessor.bufferView = bufferViewIndex
        accessor.componentType = 5126 // FLOAT
        accessor.type = type
        accessor.count = when (type) {
            "SCALAR" -> data.size / 4
            "VEC3" -> data.size / 12
            "VEC4" -> data.size / 16
            else -> throw IllegalArgumentException("Unknown type: $type")
        }
        accessors.add(accessor)
        
        return accessorIndex
    }
    
    /**
     * Convert Euler angles (in radians) to quaternion.
     * Returns [x, y, z, w]
     */
    private fun eulerToQuaternion(x: Float, y: Float, z: Float): FloatArray {
        val cy = cos(z * 0.5).toFloat()
        val sy = sin(z * 0.5).toFloat()
        val cp = cos(y * 0.5).toFloat()
        val sp = sin(y * 0.5).toFloat()
        val cr = cos(x * 0.5).toFloat()
        val sr = sin(x * 0.5).toFloat()
        
        val w = cr * cp * cy + sr * sp * sy
        val qx = sr * cp * cy - cr * sp * sy
        val qy = cr * sp * cy + sr * cp * sy
        val qz = cr * cp * sy - sr * sp * cy
        
        return floatArrayOf(qx, qy, qz, w)
    }
}
