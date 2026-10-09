# Packages the native Windows executables for machines without Java (e.g. a cloud PC):
# po-xray.exe (CLI), po-xray-mcp.exe (MCP server), the Visual C++ runtime DLLs they need (app-local,
# no installation) and INSTALL.txt, as dist/po-xray-<version>-windows-x64(.zip).
# Run after: mvn -pl po-xray-cli,po-xray-mcp -am package -Dnative -DskipTests (in a vcvars64 shell)
param(
    [string]$Version = "0.1.0-SNAPSHOT",
    [string]$VisualStudio = "C:\Program Files (x86)\Microsoft Visual Studio\2022\BuildTools"
)
$ErrorActionPreference = "Stop"
$root = Split-Path -Parent $PSScriptRoot
$name = "po-xray-$Version-windows-x64"
$out = Join-Path $root "dist\$name"

$cli = Join-Path $root "po-xray-cli\target\po-xray-cli-$Version-runner.exe"
$mcp = Join-Path $root "po-xray-mcp\target\po-xray-mcp-$Version-runner.exe"
foreach ($f in @($cli, $mcp)) {
    if (-not (Test-Path $f)) { throw "Missing $f - build the native executables first" }
}
$crt = Get-ChildItem -Path (Join-Path $VisualStudio "VC\Redist\MSVC") -Directory |
    Where-Object { $_.Name -match '^\d' } | Sort-Object { [version]$_.Name } -Descending |
    ForEach-Object { Join-Path $_.FullName "x64\Microsoft.VC143.CRT" } |
    Where-Object { Test-Path (Join-Path $_ "vcruntime140.dll") } | Select-Object -First 1
if (-not $crt) { throw "Visual C++ redistributable DLLs not found under $VisualStudio" }

if (Test-Path $out) { Remove-Item -Recurse -Force $out }
New-Item -ItemType Directory -Force $out | Out-Null
Copy-Item $cli (Join-Path $out "po-xray.exe")
Copy-Item $mcp (Join-Path $out "po-xray-mcp.exe")
Copy-Item (Join-Path $crt "vcruntime140.dll"), (Join-Path $crt "vcruntime140_1.dll") $out

@"
po-xray $Version for Windows x64 - no Java needed

1. Unzip to a folder, e.g. C:\Tools\po-xray (keep the DLLs next to the .exe files).
2. One file per PO system: %USERPROFILE%\.po-xray\systems\<system>.properties, e.g. eon-dev.properties:
     url=https://host:port
     user=...
     password=...
   optional:
     truststore=windows        (HTTPS: default = JDK certificates or the Windows store; windows | jdk | file path)
     proxy=host:port           (HTTP proxy, default direct)
   User / password can instead come from environment variables, per system (name in upper case, other
   characters as _): POXRAY_EON_DEV_USER / POXRAY_EON_DEV_PASSWORD; POXRAY_USER / POXRAY_PASSWORD apply to all.
3. Test: po-xray.exe components -s <system> -n <part of a component name>
4. Claude Code: claude mcp add -s user po-xray -- C:\Tools\po-xray\po-xray-mcp.exe
   then start a new Claude Code session; claude mcp list shows the status.
   Claude Desktop (chat): add to %APPDATA%\Claude\claude_desktop_config.json (top level, next to "preferences"):
     "mcpServers": { "po-xray": { "command": "C:\\Tools\\po-xray\\po-xray-mcp.exe" } }
   then quit Claude Desktop completely (tray icon - Quit) and start it again.
5. Update: quit the sessions / Claude Desktop using the MCP server, unzip the new package over the folder.

Objects read are kept in %USERPROFILE%\.po-xray\cache\<system> (customer data: keep it on that machine).
"@ | Set-Content -Encoding utf8 (Join-Path $out "INSTALL.txt")

$zip = "$out.zip"
if (Test-Path $zip) { Remove-Item -Force $zip }
Compress-Archive -Path (Join-Path $out "*") -DestinationPath $zip
Write-Output "Packaged $zip"
Get-ChildItem $out | ForEach-Object { Write-Output ("  {0,12:N0}  {1}" -f $_.Length, $_.Name) }
