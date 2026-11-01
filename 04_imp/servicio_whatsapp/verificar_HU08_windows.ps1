param(
    [ValidateRange(1, 50)]
    [int]$Rondas = 10
)
$ErrorActionPreference = 'Continue'
$root = Split-Path -Parent $MyInvocation.MyCommand.Path
Set-Location -LiteralPath $root
if (-not (Get-Command node -ErrorAction SilentlyContinue)) { throw 'Node.js no esta instalado.' }
if (-not (Get-Command npm.cmd -ErrorAction SilentlyContinue)) { throw 'npm.cmd no esta disponible.' }
$version = & node -p 'process.versions.node'
if ([version]$version -lt [version]'22.13.0') { throw 'Se requiere Node.js >= 22.13.0.' }
$evidencias = Join-Path $root 'evidencias_HU08_Windows'
New-Item -ItemType Directory -Path $evidencias -Force | Out-Null
@(
    "Fecha: $(Get-Date -Format 'yyyy-MM-dd HH:mm:ss')"
    "Node: $(node --version)"
    "Windows: $([Environment]::OSVersion.VersionString)"
    "Rondas: $Rondas"
) | Set-Content -LiteralPath (Join-Path $evidencias 'entorno.txt') -Encoding UTF8
for ($i = 1; $i -le $Rondas; $i++) {
    $archivo = Join-Path $evidencias ('test_{0:00}.txt' -f $i)
    & npm.cmd test *> $archivo
    if ($LASTEXITCODE -ne 0) {
        Get-Content -LiteralPath $archivo -Tail 45
        throw "Pruebas fallidas en ronda $i"
    }
    if (-not (Select-String -LiteralPath $archivo -Pattern '(?m)\bfail\s+0\b' -Quiet)) {
        throw "Resumen inesperado en ronda $i"
    }
    Write-Host "Ronda $i aprobada"
}
$archivoDemo = Join-Path $evidencias 'demo.txt'
& npm.cmd run demo *> $archivoDemo
if ($LASTEXITCODE -ne 0) { throw 'Demostracion fallida' }
if (-not (Select-String -LiteralPath $archivoDemo -Pattern '"resultado": "APROBADO"' -Quiet)) {
    throw 'La demostracion no informo APROBADO'
}
$archivoCobertura = Join-Path $evidencias 'cobertura.txt'
& node --test --experimental-test-coverage *> $archivoCobertura
if ($LASTEXITCODE -ne 0) { throw 'Verificacion de cobertura fallida' }
Write-Host "Pruebas, demostracion y cobertura completadas. Registros: $evidencias"
