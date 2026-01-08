# CLI Exporter

A command-line tool for exporting Phantasy Star Online animation files (NJM format) to FBX format, making them usable in 3D modeling software like Blender, Maya, and 3ds Max.

## Features

- Exports PSO NJ/XJ geometry files (skeleton structure)
- Exports PSO NJM animation files
- Converts to FBX ASCII format (version 7.4.0)
- Preserves bone hierarchy
- Maintains animation timing at 30 FPS
- Supports linear and spline interpolation
- Generates fat JAR for easy distribution

## Building

Build the CLI tool using Gradle:

```bash
./gradlew :cli-exporter:build
```

This will:
1. Compile the Kotlin source code
2. Generate a fat JAR with all dependencies included
3. Output the JAR to `cli-exporter/build/libs/cli-exporter.jar`

## Usage

### Running with Gradle

```bash
./gradlew :cli-exporter:run --args="<geometry-file> <animation-file> <output-file>"
```

Example:
```bash
./gradlew :cli-exporter:run --args="model.nj animation.njm output.fbx"
```

### Running the JAR

After building, you can run the standalone JAR:

```bash
java -jar cli-exporter/build/libs/cli-exporter.jar <geometry-file> <animation-file> <output-file>
```

Example:
```bash
java -jar cli-exporter/build/libs/cli-exporter.jar model.nj animation.njm output.fbx
```

### Command-line Arguments

1. **GEOMETRY** (required): Input NJ or XJ geometry file containing the skeleton structure
   - Supported formats: `.nj`, `.xj`
   - This file defines the bone hierarchy

2. **ANIMATION** (required): Input NJM animation file
   - Format: `.njm`
   - Contains keyframe animation data

3. **OUTPUT** (required): Output FBX file path
   - Format: `.fbx`
   - Will be created in FBX ASCII format (version 7.4.0)

### Help

To see the help message:

```bash
./gradlew :cli-exporter:run --args="--help"
```

Or with the JAR:

```bash
java -jar cli-exporter/build/libs/cli-exporter.jar --help
```

## Examples

### Example 1: Basic Export

```bash
# Export a basic enemy animation
./gradlew :cli-exporter:run --args="enemy_001.nj enemy_walk.njm enemy_walk.fbx"
```

### Example 2: Player Animation

```bash
# Export player character animation
java -jar cli-exporter/build/libs/cli-exporter.jar player.xj player_run.njm player_run.fbx
```

## Testing Output in Blender

To verify the exported FBX file in Blender:

1. **Open Blender** (version 3.x or later recommended)

2. **Import the FBX file:**
   - Go to `File` → `Import` → `FBX (.fbx)`
   - Select your exported `.fbx` file
   - Click `Import FBX`

3. **Verify the skeleton:**
   - Switch to `Object Mode`
   - Select the imported armature
   - The bone hierarchy should be visible in the outliner

4. **Play the animation:**
   - Open the `Timeline` or `Dope Sheet` editor
   - Press the spacebar or click the play button
   - The animation should play at 30 FPS

5. **Check bone structure:**
   - Switch to `Edit Mode` or `Pose Mode`
   - Inspect individual bones
   - Verify parent-child relationships

## Output Format

The tool generates FBX files in ASCII format (version 7.4.0) with the following structure:

- **FBXHeaderExtension**: Metadata and version information
- **GlobalSettings**: Scene settings, coordinate system, frame rate
- **Documents**: Scene hierarchy
- **Objects**: 
  - Bone models (LimbNode type)
  - Animation curves (position, rotation, scale)
  - Animation stack and layer
- **Connections**: Links between bones and animation curves

## Technical Details

### Frame Rate
- PSO animations use **30 FPS**
- This is preserved in the FBX export
- FBX KTime units: 46186158000 = 1 second

### Coordinate System
- Both PSO and FBX use Y-up, right-handed coordinates
- No coordinate transformation is applied

### Interpolation
- Linear interpolation → FBX linear
- Spline interpolation → FBX cubic
- UserFunction → FBX linear (fallback)

### Bone Naming
- Bones are named sequentially: `Bone_0`, `Bone_1`, etc.
- Names are based on the bone index in the hierarchy

## Limitations and Known Issues

1. **Quaternion tracks**: Currently not supported (will be skipped with a warning)
2. **Texture export**: Textures are not included in the export
3. **Mesh geometry**: Only skeleton/bones are exported, not the actual mesh geometry
4. **Complex animations**: Very complex animations with custom interpolation functions may not convert perfectly
5. **Animation tracks**: If animation has more tracks than bones in the geometry, extra tracks are ignored

## Future Enhancements

The following features are planned but not yet implemented:

- Support for batch processing multiple animations
- FBX binary format export
- glTF/GLB export as an alternative format
- Texture export alongside geometry
- Quaternion track conversion to Euler angles
- Full mesh geometry export
- GUI wrapper for the CLI tool

## Error Handling

The tool provides clear error messages for common issues:

- **File not found**: Checks if input files exist before processing
- **Invalid format**: Validates file extensions and content
- **Parse errors**: Reports detailed information about parsing failures
- **Mismatched data**: Warns if animation has more tracks than bones

## Development

### Project Structure

```
cli-exporter/
├── build.gradle.kts          # Build configuration
├── README.md                  # This file
└── src/main/kotlin/world/phantasmal/cliexporter/
    ├── Main.kt               # CLI entry point and argument parsing
    └── FbxExporter.kt        # FBX format generation logic
```

### Dependencies

- **Kotlin**: JVM target 17
- **psolib**: PSO file format parsing (NJ/XJ/NJM)
- **core**: Core utilities
- **Clikt**: Command-line argument parsing

### Adding Tests

To add unit tests for the exporter:

1. Create test directory: `cli-exporter/src/test/kotlin/world/phantasmal/cliexporter/`
2. Add test dependencies in `build.gradle.kts`
3. Create test files with PSO sample data
4. Write tests for parsing and export logic

## Contributing

When contributing to this tool:

1. Follow the existing Kotlin code style
2. Add tests for new functionality
3. Update this README with new features
4. Ensure FBX output is valid and imports correctly in Blender

## License

This tool is part of the Phantasmal World project. See the main project LICENSE file for details.
