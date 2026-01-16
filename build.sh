#!/bin/bash
# Build Werchat from WSL by calling the Windows batch file
cd "$(dirname "$0")"
cmd.exe /c build-werchat.bat
