@echo off
rem ============================================================
rem  McSkillXray - LEGACY ASM build (NOT deployed).
rem  Historic build.cmd: compiles src/ (XrayPlugin + XrayTransformer,
rem  javassist bytecode transformation) WITHOUT the menu. Kept for
rem  reference only - the deployed jar is built by build.cmd which
rem  delegates to build-mixin.cmd (mixin-src + stubs).
rem ============================================================
setlocal
set "ROOT=%~dp0"
set "JDK=C:\Program Files\Eclipse Adoptium\jdk-21.0.10.7-hotspot\bin"
set "CLIENT=C:\Users\renat\McSkill\clients\Galaxy_1.7.10"
set "JVST=%ROOT%..\agent\lib\javassist-3.33.0-GA.jar"
set "FORGE=%CLIENT%\forge.jar"
set "RFB=%CLIENT%\libraries\+RetroFuturaBootstrap-1.1.0.jar"

if not exist "%ROOT%build" mkdir "%ROOT%build"
if not exist "%ROOT%build\classes" mkdir "%ROOT%build\classes"
if not exist "%ROOT%build\jvst" mkdir "%ROOT%build\jvst"

echo [1/3] javac ...
"%JDK%\javac.exe" -encoding UTF-8 -source 8 -target 8 -Xlint:-options ^
  -cp "%JVST%;%FORGE%;%RFB%" -d "%ROOT%build\classes" ^
  "%ROOT%src\com\mcskill\xray\XrayPlugin.java" "%ROOT%src\com\mcskill\xray\XrayTransformer.java"
if errorlevel 1 exit /b 1

echo [2/3] bundle javassist ...
pushd "%ROOT%build\jvst"
"%JDK%\jar.exe" xf "%JVST%"
popd

echo [3/3] jar cfm ...
"%JDK%\jar.exe" cfm "%ROOT%build\xray-mcskill-legacy-asm.jar" "%ROOT%manifest.txt" -C "%ROOT%build\classes" . -C "%ROOT%build\jvst" . -C "%ROOT%build\res" .
if errorlevel 1 exit /b 1

echo DONE: %ROOT%build\xray-mcskill-legacy-asm.jar (reference only)
endlocal