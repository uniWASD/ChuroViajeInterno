# Verificacion reproducible de T-091 en Windows (PowerShell 5.1 o 7).
# No instala dependencias ni utiliza secretos reales.
param(
    [ValidateRange(1, 100)]
    [int]$Rondas = 10
)

# En Windows PowerShell 5.1 los avisos de procesos nativos pueden ir por stderr.
# Verificar el codigo de salida explicita e inmediatamente en lugar de Stop.
$ErrorActionPreference = 'Continue'
$root = Split-Path -Parent $MyInvocation.MyCommand.Path
Set-Location -LiteralPath $root
$evidencias = Join-Path $root 'evidencias_T91_Windows'
New-Item -ItemType Directory -Path $evidencias -Force | Out-Null

if (-not (Get-Command node -ErrorAction SilentlyContinue)) { throw 'Node.js no esta instalado.' }
if (-not (Get-Command npm.cmd -ErrorAction SilentlyContinue)) { throw 'npm.cmd no esta disponible.' }
$version = & node -p 'process.versions.node'
if ([version]$version -lt [version]'22.13.0') { throw 'Se requiere Node.js >= 22.13.0.' }

@(
    "Fecha: $(Get-Date -Format 'yyyy-MM-dd HH:mm:ss')"
    "Ruta: $root"
    "Windows: $([Environment]::OSVersion.VersionString)"
    "Node: $(node --version)"
    "Node arch: $(node -p 'process.arch')"
    "npm: $(npm.cmd --version)"
) | Set-Content -LiteralPath (Join-Path $evidencias 'entorno.txt') -Encoding UTF8

Write-Host "Ejecutando $Rondas rondas de pruebas ..." -ForegroundColor Cyan
for ($i=1; $i -le $Rondas; $i++) {
    $log = Join-Path $evidencias ('test_{0:00}.txt' -f $i)
    & npm.cmd test *> $log
    if ($LASTEXITCODE -ne 0) {
        Get-Content -LiteralPath $log -Tail 65
        throw "Fallo en la ronda $i. Revisar $log"
    }
    $resumen = Get-Content -LiteralPath $log -Tail 12 | Out-String
    if ($resumen -notmatch '(?m)\bfail\s+0\b') {
        throw "Resumen de pruebas inesperado en ronda $i. Revisar $log"
    }
    Write-Host "RONDA $i / ${Rondas}: APROBADA" -ForegroundColor Green
}

$logDemo = Join-Path $evidencias 'demo.txt'
& npm.cmd run demo *> $logDemo
if ($LASTEXITCODE -ne 0) {
    Get-Content -LiteralPath $logDemo -Tail 50
    throw "Fallo la demo HTTP. Revisar $logDemo"
}
if (-not (Select-String -LiteralPath $logDemo -Pattern '"resultado": "APROBADO"' -Quiet)) {
    throw 'La demostracion termino pero no informo resultado APROBADO.'
}
Write-Host 'DEMO HTTP: APROBADA' -ForegroundColor Green

$logCobertura = Join-Path $evidencias 'cobertura.txt'
& node --test --experimental-test-coverage *> $logCobertura
if ($LASTEXITCODE -ne 0) {
    Get-Content -LiteralPath $logCobertura -Tail 65
    throw "Fallo el informe de cobertura. Revisar $logCobertura"
}
Write-Host 'COBERTURA: GENERADA SIN FALLOS' -ForegroundColor Green
Write-Host "Evidencias guardadas en: $evidencias" -ForegroundColor Cyan
Write-Host 'Revisar resumen: fail 0; en Windows es normal skipped 1 (permisos POSIX).' -ForegroundColor Cyan
