@echo off
set JAVA_HOME=C:\Program Files\Eclipse Adoptium\jdk-21.0.9.10-hotspot
set PATH=%JAVA_HOME%\bin;%PATH%
set MODS_DIR=%APPDATA%\Hytale\UserData\Mods
echo Using Java:
java -version
echo.
echo Building Werchat...
call gradlew.bat build --no-daemon
echo.
if exist "build\libs\Werchat-*.jar" (
    echo SUCCESS! JAR file built:
    dir /b build\libs\*.jar
    echo.
    if exist "%MODS_DIR%\Werchat-*.jar" (
        echo Deployed JAR in mods folder:
        dir /b "%MODS_DIR%\Werchat-*.jar"
        echo Path: %MODS_DIR%
    )
) else (
    echo FAILED! Check errors above.
)
pause
