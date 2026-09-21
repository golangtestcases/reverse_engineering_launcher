# Batch helper: writes a javac @file argument list for all stub sources.
# Each line is a fully quoted path (paths contain spaces), ASCII encoded.
param(
    [string]$StubsDir,
    [string]$OutFile
)

$lines = Get-ChildItem -LiteralPath $StubsDir -Recurse -Filter *.java |
    ForEach-Object { '"' + $_.FullName + '"' }
Set-Content -LiteralPath $OutFile -Value $lines -Encoding ASCII