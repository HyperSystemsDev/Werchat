@echo off
set JAVA_HOME=C:\Program Files\Eclipse Adoptium\jdk-21.0.9.10-hotspot
set PATH=%JAVA_HOME%\bin;%PATH%
echo Using Java:
java -version
echo.
echo Building Werchat...
call gradlew.bat jar --no-daemon
echo.
if exist "build\libs\Werchat-*.jar" (
    echo SUCCESS! JAR file built:
    dir /b build\libs\*.jar
) else (
    echo FAILED! Check errors above.
)
pause
