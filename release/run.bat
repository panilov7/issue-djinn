@echo off
rem issue-djinn launcher: starts the app from the folder the release zip was
rem extracted into. Requires Java 21+. Data directory, host, and port can be
rem overridden with the ISSUE_DJINN_DATA_DIR, ISSUE_DJINN_HOST, and
rem ISSUE_DJINN_PORT environment variables.
setlocal
if not exist "%~dp0issue-djinn.jar" (
    echo error: issue-djinn.jar not found next to run.bat 1>&2
    exit /b 1
)
java -jar "%~dp0issue-djinn.jar"