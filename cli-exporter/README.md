# PSO Animation Exporter CLI Tool

A command-line tool for exporting Phantasy Star Online (PSO) animation files (NJM format) to industry-standard 3D formats.

## Supported Formats

This tool can export PSO animations to the following formats:

| Format | File Type | Best For | Software Support |
|--------|-----------|----------|------------------|
| **FBX** | ASCII | Industry standard workflows, professional software | Blender, Maya, 3ds Max, Unreal Engine, Unity |
| **glTF** | JSON | Web applications, modern pipelines, easy debugging | Blender, Unreal Engine 5+, Unity, Godot, Three.js |
| **GLB** | Binary | Compact single-file distribution | Same as glTF |

### Format Details

**FBX (Filmbox)**
- Industry standard format developed by Autodesk
- ASCII format version 7.4.0
- Excellent compatibility with professional 3D software
- Best for game development and film production workflows

**glTF 2.0 (GL Transmission Format)**
- Open standard maintained by Khronos Group
- JSON format (.gltf) for readability and debugging
- Binary format (.glb) for compact distribution
- Modern format with excellent web support
- Growing adoption in game engines

## Building

Build the CLI tool using Gradle:

```bash
./gradlew :cli-exporter:build
```

This will create a fat JAR with all dependencies at:
```
cli-exporter/build/libs/cli-exporter.jar
```

## Usage

The tool requires three arguments:
1. **Geometry file**: NJ or XJ format file containing the skeleton structure
2. **Animation file**: NJM format file containing the animation data
3. **Output file**: Target file path with extension (.fbx, .gltf, or .glb)

The output format is automatically detected from the file extension.

### Using Gradle Run

```bash
# Export as FBX ASCII
./gradlew :cli-exporter:run --args="model.nj animation.njm output.fbx"

# Export as glTF JSON
./gradlew :cli-exporter:run --args="model.nj animation.njm output.gltf"

# Export as glTF binary
./gradlew :cli-exporter:run --args="model.nj animation.njm output.glb"

# Using XJ geometry files
./gradlew :cli-exporter:run --args="model.xj animation.njm output.gltf"
```

### Using JAR Directly

After building, you can run the JAR directly:

```bash
java -jar cli-exporter/build/libs/cli-exporter.jar model.nj animation.njm output.fbx
```

## Examples

### Example 1: Export to FBX for Blender

```bash
./gradlew :cli-exporter:run --args="player.nj walk.njm walk_anim.fbx"
```

Then in Blender:
1. File → Import → FBX
2. Select `walk_anim.fbx`
3. The skeleton and animation will be imported

### Example 2: Export to glTF for Web

```bash
./gradlew :cli-exporter:run --args="enemy.nj attack.njm attack.gltf"
```

Then use in Three.js:
```javascript
const loader = new GLTFLoader();
loader.load('attack.gltf', (gltf) => {
    scene.add(gltf.scene);
    const mixer = new THREE.AnimationMixer(gltf.scene);
    mixer.clipAction(gltf.animations[0]).play();
});
```

### Example 3: Export to GLB for Unreal Engine

```bash
./gradlew :cli-exporter:run --args="boss.nj idle.njm idle.glb"
```

Then in Unreal Engine 5:
1. Content Browser → Import
2. Select `idle.glb`
3. Import as Skeletal Mesh with animations

## Technical Details

### Frame Rate
PSO animations use a frame rate of **30 FPS**. This is correctly handled during export:
- FBX: Converted to KTime units (46186158000 per second)
- glTF: Converted to seconds (time = frame / 30.0)

### Coordinate System
- PSO: Y-up, right-handed
- FBX: Y-up, right-handed (no conversion needed)
- glTF: Y-up, right-handed (no conversion needed)

### Rotation Representation
- PSO: Euler angles (radians) or quaternions
- FBX: Euler angles (degrees) - converted automatically
- glTF: Quaternions - converted from Euler angles when needed

### Interpolation Types
Animation interpolation is mapped as follows:

| PSO Interpolation | FBX | glTF |
|-------------------|-----|------|
| Linear | Linear | LINEAR |
| Spline | Cubic | CUBICSPLINE |
| UserFunction | Linear (fallback) | LINEAR (fallback) |

## Testing

### Testing FBX Export

1. Export an animation to FBX format
2. Import into Blender 3.x+:
   ```
   File → Import → FBX (.fbx)
   ```
3. Verify:
   - Skeleton hierarchy appears in the outliner
   - Timeline shows correct frame range
   - Animation plays at 30 FPS
   - Bone transforms animate correctly

### Testing glTF Export

1. Export an animation to glTF format
2. Import into Blender 3.x+:
   ```
   File → Import → glTF 2.0 (.gltf/.glb)
   ```
3. Or view online at: https://gltf-viewer.donmccurdy.com
4. Verify:
   - Skeleton hierarchy is correct
   - Animation data is present
   - Playback is smooth
   - Keyframe timing is accurate

### Testing in Game Engines

**Unreal Engine 5:**
- Import .fbx or .glb files directly
- Check that skeleton matches expected hierarchy
- Verify animation plays correctly on the skeletal mesh

**Unity:**
- Import .fbx or .gltf files via Asset Import
- Configure as humanoid or generic rig
- Test animation in the Animation window

## Limitations and Known Issues

1. **Texture Export**: Not currently supported. Only skeleton and animation data are exported.
2. **Quaternion to Euler**: FBX format requires Euler angles. Quaternion animation tracks are currently skipped in FBX export.
3. **Mesh Geometry**: Only skeleton structure is exported. Mesh data from NJ/XJ files is not yet included in the output.
4. **Multiple Animations**: The tool processes one animation at a time. Batch processing is not yet supported.
5. **Animation Baking**: Assumes all keyframes are explicitly defined in the NJM file.

## Troubleshooting

### Error: "Geometry file must have .nj or .xj extension"
- Ensure your geometry file has the correct extension
- PSO uses .nj (Ninja) or .xj (XJ) formats for models

### Error: "No objects found in geometry file"
- The geometry file may be corrupted or invalid
- Try a different geometry file

### Error: "Output file must have .fbx, .gltf, or .glb extension"
- The tool auto-detects format from extension
- Make sure your output file ends with .fbx, .gltf, or .glb

### Animation doesn't play in Blender
- Check that the timeline frame range matches the animation
- Verify the skeleton is selected when playing
- Make sure "Auto Keying" is off

### Skeleton appears but animation is missing
- Verify the NJM file contains animation data
- Check that bone count matches between geometry and animation
- Look for error messages in the console output

## Future Enhancements

Potential features for future versions:
- Mesh geometry export with vertex skinning
- Texture coordinate export
- Material export
- Batch processing of multiple animations
- Additional format support (Collada, USD)
- GUI wrapper for easier use
- Progress bars for large exports
- Verbose/debug output modes

## License

This tool is part of the Phantasmal World project. See the main repository LICENSE file for details.
