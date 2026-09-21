@echo off
rem ============================================================
rem  Build without Maven (javac + jar).
rem  Requires: javac/jar on PATH, javassist jar in lib\.
rem  Outputs:
rem    out\launcher-agent.jar  - agent uber-jar (agent classes + javassist)
rem    out\attach              - AttachTool classes
rem ============================================================
setlocal
set "ROOT=%~dp0"
set "SRC=%ROOT%src"
set "OUT=%ROOT%out"
set "LIB=%ROOT%lib\javassist-3.33.0-GA.jar"

if not exist "%LIB%" (
  echo [ERROR] %LIB% not found.
  echo Download from:
  echo   https://repo1.maven.org/maven2/org/javassist/javassist/3.33.0-GA/javassist-3.33.0-GA.jar
  exit /b 1
)

if not exist "%OUT%" mkdir "%OUT%"

echo [1/3] compile agent
javac -encoding UTF-8 -cp "%LIB%" -d "%OUT%\agent" "%SRC%\com\mcskill\bypass\LauncherAgent.java" || exit /b 1

echo [2/3] compile attach tool
javac --add-modules jdk.attach -encoding UTF-8 -d "%OUT%\attach" "%SRC%\com\mcskill\bypass\AttachTool.java" || exit /b 1

echo [3/3] assemble uber agent jar
rd /s /q "%OUT%\jvst" 2>nul
mkdir "%OUT%\jvst"
pushd "%OUT%\jvst"
jar xf "%LIB%"
popd
(
  echo Manifest-Version: 1.0
  echo Agent-Class: com.mcskill.bypass.LauncherAgent
  echo Premain-Class: com.mcskill.bypass.LauncherAgent
  echo Can-Redefine-Classes: true
  echo Can-Retransform-Classes: true
  echo.
) > "%OUT%\MANIFEST.MF"
rem Note: jar uf drops META-INF/MANIFEST.MF (JDK 21 jar quirk), so we pack
rem agent classes + javassist classes in ONE cfm invocation.
jar cfm "%OUT%\launcher-agent.jar" "%OUT%\MANIFEST.MF" -C "%OUT%\agent" . -C "%OUT%\jvst" . || exit /b 1
rd /s /q "%OUT%\jvst"

echo.
echo DONE:
echo   %OUT%\launcher-agent.jar
echo   %OUT%\attach\com\mcskill\bypass\AttachTool.class
endlocal