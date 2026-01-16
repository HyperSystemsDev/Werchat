# Building Werchat

## Quick Build

**From Windows:**
```
build-werchat.bat
```

**From WSL:**
```bash
cmd.exe /c build-werchat.bat
```

## Requirements

- Java 21 (at `C:\Program Files\Eclipse Adoptium\jdk-21.0.9.10-hotspot`)
- Gradle (wrapper included)
- HytaleServer.jar in `libs/` directory

## Output

The built JAR will be at `build/libs/Werchat-<version>.jar`

The build automatically deploys to `%APPDATA%/Hytale/UserData/Mods/`

## Troubleshooting

If build fails with Java version issues:
1. Check JAVA_HOME is set correctly in build-werchat.bat
2. Run `java -version` to verify Java is accessible
3. Use `--no-daemon` flag if Gradle daemon has stale state
