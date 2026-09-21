@echo off
rem ============================================================
rem  McSkillXray 1.7.10 - main build for Galaxy_1.7.10
rem  Builds the MIXIN version (the one actually deployed in the
rem  game, with the redesigned menu) and installs it into
rem  %CLIENT%\mods\xray-mcskill.jar (previous jar -> .bak).
rem
rem  Output: build\xray-mcskill.jar
rem
rem  Legacy ASM build (no menu, not deployed) is kept in
rem  build-legacy-asm.cmd for reference.
rem ============================================================
setlocal
set "ROOT=%~dp0"
call "%ROOT%build-mixin.cmd"
if errorlevel 1 (
  echo BUILD FAILED
  exit /b 1
)
endlocal