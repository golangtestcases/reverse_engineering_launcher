# McSkillXray mixin build - mirrors the deployed mods\xray-mcskill.jar layout:
#   com/mcskill/xray/* + mixin/* (javac from mixin-src, stubs compiled
#   separately so they never leak into the jar),
#   com/gamerforea/fontfix/* + assets from FontFix-1.7.10.jar,
#   mcmod.info, mixins.mcskillxray.json, manifest.txt.
param(
    [string]$Root = (Split-Path -Parent $MyInvocation.MyCommand.Path)
)

$ErrorActionPreference = 'Stop'

# cmd.exe passes "C:\...\xray\" - the trailing backslash before the closing
# quote is mangled into a stray quote char by Windows arg parsing; strip it.
$Root = $Root.TrimEnd('"', '\', ' ', "`t")

$jdk       = 'C:\Program Files\Eclipse Adoptium\jdk-21.0.10.7-hotspot\bin'
$javac     = Join-Path $jdk 'javac.exe'
$jar       = Join-Path $jdk 'jar.exe'
$client    = 'C:\Users\renat\McSkill\clients\Galaxy_1.7.10'
$forge     = Join-Path $client 'forge.jar'
$rfb       = Join-Path $client 'libraries\+RetroFuturaBootstrap-1.1.0.jar'
$unimixins = Join-Path $client 'mods\++unimixins-all-1.7.10-0.3.1.jar'
$fontfix   = Join-Path $client 'mods\FontFix-1.7.10.jar'

$stubs  = Join-Path $Root 'mixin-src\stubs'
$src    = Join-Path $Root 'mixin-src\com\mcskill\xray'
$out    = Join-Path $Root 'build\mixin-classes'
$outSt  = Join-Path $Root 'build\stubs-classes'
$stage  = Join-Path $Root 'build\mixin-stage'
$built  = Join-Path $Root 'build\xray-mcskill.jar'
$target = Join-Path $client 'mods\xray-mcskill.jar'
$manifest = Join-Path $Root 'manifest.txt'

foreach ($d in @($out, $outSt, $stage)) {
    if (Test-Path $d) { Remove-Item -Recurse -Force $d }
    New-Item -ItemType Directory -Path $d -Force | Out-Null
}

Write-Host '[1/4] javac stubs + mixin-src ...'
$stubFiles = @(Get-ChildItem -LiteralPath $stubs -Recurse -Filter *.java | ForEach-Object { $_.FullName })
& $javac -encoding UTF-8 -source 8 -target 8 -nowarn -d $outSt $stubFiles
if ($LASTEXITCODE -ne 0) { exit $LASTEXITCODE }

$srcFiles = @(
    (Join-Path $src 'XrayMod.java'),
    (Join-Path $src 'Keybinds.java'),
    (Join-Path $src 'State.java'),
    (Join-Path $src 'RenderState.java'),
    (Join-Path $src 'XrayMenu.java'),
    (Join-Path $src 'XrayPlugin.java'),
    (Join-Path $src 'mixin\MixinBlock.java'),
    (Join-Path $src 'mixin\MixinBrightness.java'),
    (Join-Path $src 'mixin\MixinForceChunk.java'),
    (Join-Path $src 'mixin\MixinRenderBlocks.java')
)
& $javac -encoding UTF-8 -source 8 -target 8 -Xlint:-options -cp "$outSt;$forge;$rfb;$unimixins" -d $out $srcFiles
if ($LASTEXITCODE -ne 0) { exit $LASTEXITCODE }

Write-Host '[2/4] stage resources ...'
Copy-Item (Join-Path $Root 'mixin-res\mcmod.info') (Join-Path $stage 'mcmod.info') -Force
Copy-Item (Join-Path $Root 'mixin-res\mixins.mcskillxray.json') (Join-Path $stage 'mixins.mcskillxray.json') -Force
if (Test-Path $fontfix) {
    Push-Location $stage
    try { & $jar xf $fontfix com assets; if ($LASTEXITCODE -ne 0) { exit $LASTEXITCODE } }
    finally { Pop-Location }
} else {
    Write-Host '  [WARN] FontFix-1.7.10.jar not found - no cyrillic font assets'
}
Copy-Item (Join-Path $out '*') $stage -Recurse -Force

Write-Host '[3/4] jar cfm ...'
& $jar cfm $built $manifest -C $stage .
if ($LASTEXITCODE -ne 0) { exit $LASTEXITCODE }

Write-Host '[4/4] deploy to client mods ...'
if (Test-Path $target) { Copy-Item $target "$target.bak" -Force }
Copy-Item $built $target -Force

Write-Host ''
Write-Host ("DONE: {0}  ->  {1}" -f $built, $target)