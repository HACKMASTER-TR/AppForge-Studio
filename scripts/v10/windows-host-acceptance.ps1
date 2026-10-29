$ErrorActionPreference = 'Stop'
Set-StrictMode -Version Latest
if (-not $IsWindows) { throw 'Windows host required' }
function Invoke-Checked([scriptblock]$Command) {
  & $Command
  if ($LASTEXITCODE -ne 0) { throw "Native command failed: $LASTEXITCODE" }
}
$env:CSC_IDENTITY_AUTO_DISCOVERY = 'false'
Invoke-Checked { npm run dist --prefix windows-host }
$base = Join-Path $pwd 'windows-host/dist/AppForge-Windows-Host-v1.exe'
$bytes = [IO.File]::ReadAllBytes($base)
if ($bytes[0] -ne 0x4d -or $bytes[1] -ne 0x5a) { throw 'Missing PE header' }
if ((Get-AuthenticodeSignature $base).Status -ne 'NotSigned') { throw 'Expected unsigned test binary' }
$smoke = Join-Path $pwd 'windows-host/dist/phase7-smoke.exe'
Copy-Item $base $smoke
Invoke-Checked { node windows-host/scripts/append-smoke-payload.cjs $smoke }
$env:LOCALAPPDATA = Join-Path $env:RUNNER_TEMP 'v10-local-data'
$env:APPFORGE_HOST_SMOKE_EXIT = '1'
for ($i = 1; $i -le 2; $i++) {
  $result = Join-Path $env:RUNNER_TEMP "v10-smoke-$i.json"
  if (Test-Path $result) { throw 'Stale smoke evidence' }
  $env:APPFORGE_HOST_SMOKE_FILE = $result
  $process = Start-Process -FilePath $smoke -PassThru
  if (-not $process.WaitForExit(60000)) { $process.Kill(); throw 'Launch timeout' }
  if ($process.ExitCode -ne 0) { throw 'Launch failed' }
  $data = Get-Content $result -Raw | ConvertFrom-Json
  if ($data.payloadLoaded -ne $true -or $data.smokeState.ready -ne $true) { throw 'Payload failed' }
  if ([int]$data.smokeState.localStorageCount -ne $i -or [int]$data.smokeState.indexedDbCount -ne $i) { throw 'Persistence failed' }
}
Write-Host 'Unsigned Windows packaging/launch/relaunch PASS'
