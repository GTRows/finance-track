@REM Maven Wrapper for Windows
@echo off
setlocal

set "MAVEN_HOME=%USERPROFILE%\.m2\wrapper\dists\apache-maven-3.9.6"
set "MAVEN_CMD=%MAVEN_HOME%\bin\mvn.cmd"

if exist "%MAVEN_CMD%" goto execute

echo Downloading Maven...
set "DIST_URL=https://repo1.maven.org/maven2/org/apache/maven/apache-maven/3.9.6/apache-maven-3.9.6-bin.zip"
set "TMPFILE=%TEMP%\maven.zip"
curl -sL "%DIST_URL%" -o "%TMPFILE%"
powershell -Command "Expand-Archive -Force '%TMPFILE%' '%USERPROFILE%\.m2\wrapper\dists'"
del "%TMPFILE%"

:execute
"%MAVEN_CMD%" %*
