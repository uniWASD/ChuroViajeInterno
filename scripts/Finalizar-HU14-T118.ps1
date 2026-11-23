[CmdletBinding()]
param(
    [string]$Repo = (Split-Path -Parent $PSScriptRoot),
    [string]$CsvAnh = ""
)

$ErrorActionPreference = 'Stop'

if (-not (Test-Path -LiteralPath $Repo -PathType Container)) {
    throw "No existe la carpeta del proyecto: $Repo"
}

$PythonDir = Join-Path $Repo '05_pro\PruebaApiANH'
$BackendDir = Join-Path $Repo '04_imp\backend_java'
if ([string]::IsNullOrWhiteSpace($CsvAnh)) { $CsvAnh = Join-Path $PythonDir 'salida_tarija\20261123T172823_989807Z\estaciones_tarija.csv' }
Write-Host "Repositorio validado: $Repo"
$Pares = Join-Path $PythonDir 'vinculacion\evidencias_t118\pares_para_revision_final_delegada.csv'
$EvidenceDir = Join-Path $PythonDir 'vinculacion\evidencias_t118\ejecuciones'
New-Item -ItemType Directory -Force -Path $EvidenceDir | Out-Null
$LogE2E = Join-Path $EvidenceDir 'T118_V14_E2E.log'
$LogFull = Join-Path $EvidenceDir 'T118_V14_FULL.log'

foreach ($path in @($PythonDir, $BackendDir, $CsvAnh, $Pares)) {
    if (-not (Test-Path -LiteralPath $path)) {
        throw "Falta un archivo o carpeta requerida: $path"
    }
}

# En Windows PowerShell 5.1 el stderr de un programa nativo puede convertirse
# en ErrorRecord. Un aviso de Maven no debe interrumpir el registro. La
# decisión de éxito se toma por el código de salida del proceso y por Surefire.
function Invoke-MavenConLog {
    param([string[]]$Argumentos, [string]$Registro)
    $ErrorActionPreference = 'Continue'
    $PSNativeCommandUseErrorActionPreference = $false
    if (Test-Path -LiteralPath $Registro) {
        Remove-Item -LiteralPath $Registro -ErrorAction Stop
    }
    & .\mvnw.cmd @Argumentos 2>&1 |
        ForEach-Object { $_.ToString() } |
        Tee-Object -FilePath $Registro -ErrorAction Stop
    $codigo = $LASTEXITCODE
    if ($codigo -ne 0) {
        throw "Maven terminó con código $codigo. Revisa $Registro"
    }
    if (-not (Test-Path -LiteralPath $Registro -PathType Leaf)) {
        throw "No se guardó el registro Maven: $Registro"
    }
}

Write-Host "=== 1/4: Pruebas Python T-118 ===" -ForegroundColor Cyan
Set-Location $PythonDir
python -m unittest discover -s .\vinculacion -v
if ($LASTEXITCODE -ne 0) { throw "Fallaron las pruebas Python (código $LASTEXITCODE)." }

Write-Host "=== 2/4: Migración V14 + prueba E2E GET /surtidores ===" -ForegroundColor Cyan
Set-Location $BackendDir
Invoke-MavenConLog -Argumentos @('-B', '-Dtest=MigracionesPostgisTest,ConsultaAnhVinculosRealesGetIntegracionTest', 'test') -Registro $LogE2E

Write-Host "=== 3/4: Suite completa del backend ===" -ForegroundColor Cyan
Invoke-MavenConLog -Argumentos @('-B', 'clean', 'test') -Registro $LogFull

Write-Host "=== 4/4: Verificación documental del cierre ===" -ForegroundColor Cyan
Set-Location $PythonDir
python .\vinculacion\verificar_cierre.py `
    --pares $Pares `
    --fuente-anh $CsvAnh `
    --evidencia-maven $LogE2E `
    --evidencia-suite $LogFull `
    --estricto
if ($LASTEXITCODE -ne 0) { throw "El cierre aún tiene pendientes. Revisa el ESTADO_CIERRE.md generado." }

Write-Host "HU-14 / T-118: CIERRE TÉCNICO COMPLETO" -ForegroundColor Green
Write-Host "Evidencia E2E: $LogE2E"
Write-Host "Suite completa: $LogFull"
Write-Host "Informe: $PythonDir\vinculacion\evidencias_t118\estado_cierre\ESTADO_CIERRE.md"
