@echo off
rem ============================================================
rem  McSkillXray - MIXIN build wrapper.
rem  Real work happens in build-mixin.ps1 (avoids cmd.exe quoting/
rem  codepage issues with the cyrillic paths). Output:
rem    build\xray-mcskill.jar -> %CLIENT%\mods\xray-mcskill.jar (+ .bak)
rem ============================================================
setlocal
set "ROOT=%~dp0"
powershell -NoProfile -ExecutionPolicy Bypass -File "%ROOT%build-mixin.ps1"
if errorlevel 1 (
  echo BUILD FAILED
  exit /b 1
)
echo BUILD OK
endlocal