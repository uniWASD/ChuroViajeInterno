# T-117: pruebas autocontenidas local + Oracle (Windows PowerShell 5.1+).
# Por defecto se ejecutan SOLO respuestas simuladas; las llamadas reales exigen un switch.
param(
    [string]$IPServidor = "",
    [string]$LlaveSSH = (Join-Path $HOME '.ssh\churoviaje-deploy'),
    [string]$DatosLocal = "",
    [switch]$SoloLocal,
    [switch]$ConsultaRealOracle,
    [switch]$PedirIP
)
$ErrorActionPreference = 'Stop'
$base = (Resolve-Path (Join-Path $PSScriptRoot '..')).Path
Push-Location $base
try {
    if ([string]::IsNullOrWhiteSpace($DatosLocal)) { $DatosLocal = Join-Path $base 'datos-local' }
    $localCSV = Join-Path $DatosLocal 'medicion-local.csv'
    $sesion = Get-Date -Format 'yyyyMMdd-HHmmss'
    $carpetaAnalisis = Join-Path $env:TEMP ('ChuroViaje_T117\windows-' + $sesion)
    New-Item -ItemType Directory -Path $carpetaAnalisis -Force | Out-Null
    $env:PYTHONIOENCODING = 'utf-8'
    $python = Get-Command python -ErrorAction SilentlyContinue
    if (-not $python) { throw 'Falta Python 3 en PATH. Instala Python 3.10 o superior.' }
    $argsTest = @((Join-Path $PSScriptRoot 'ejecutar_suite.py'), '--salida', (Join-Path $carpetaAnalisis 'local'))
    if (Test-Path $localCSV) { $argsTest += @('--datos-local', $DatosLocal) }
    Write-Host '== Suite LOCAL: simulación HTTP y calendario virtual, sin nuevas consultas a ANH ==' -ForegroundColor Cyan
    & python @argsTest
    if ($LASTEXITCODE -ne 0) { throw 'Fallaron las pruebas locales. Revisa el informe generado en analisis.' }
    if ($PedirIP) { $IPServidor = Read-Host 'IP pública de Oracle (no se guarda en el proyecto)' }
    if ($SoloLocal) {
        Write-Host 'PRUEBAS LOCALES APROBADAS. No se contactó a Oracle ni a ANH.' -ForegroundColor Green
        return
    }
    if ([string]::IsNullOrWhiteSpace($IPServidor)) {
        Write-Host 'Modo local completado. Para probar también Oracle, usa -IPServidor <IP>.' -ForegroundColor Green
        return
    }
    if ($IPServidor -notmatch '^[0-9]{1,3}(\.[0-9]{1,3}){3}$') {
        throw 'IPServidor debe ser IPv4 en formato numérico, sin comandos ni texto.'
    }
    if (-not (Test-Path $LlaveSSH)) { throw "No existe la llave PRIVADA SSH: $LlaveSSH" }
    if (-not (Get-Command ssh -ErrorAction SilentlyContinue)) { throw 'No existe ssh de Windows OpenSSH.' }
    if (-not (Get-Command scp -ErrorAction SilentlyContinue)) { throw 'No existe scp de Windows OpenSSH.' }
    # No hacer una nueva ronda contra ANH desde Oracle si el proceso local sigue activo.
    if ($ConsultaRealOracle) {
        try {
            $activos = @(Get-CimInstance Win32_Process -Filter "Name = 'java.exe'" -ErrorAction Stop |
                Where-Object { $_.CommandLine -match 'medicion-anh[.]jar' -and $_.CommandLine -match '--origen\s+local' })
            if ($activos.Count -gt 0) { throw 'El medidor local sigue activo: detén la ventana Java con Ctrl+C.' }
        } catch {
            if ($_.Exception.Message -match 'sigue activo') { throw }
            throw 'No se pudo comprobar que el medidor local esté detenido; omitiendo la ronda real por seguridad.'
        }
        if (Test-Path $localCSV) {
            $filas = @(Import-Csv $localCSV)
            $catalogos = @($filas | Where-Object { $_.tipo -eq 'catalogo' -and $_.producto -eq '0' -and $_.intento -eq '1' })
            if ($catalogos.Count -gt 0) {
                $ultima = [DateTimeOffset]::Parse($catalogos[-1].hora_utc)
                $falta = 600 - [math]::Floor(([DateTimeOffset]::UtcNow - $ultima.ToUniversalTime()).TotalSeconds)
                if ($falta -gt 0) { Write-Host "Separación de seguridad ANH: $falta segundos."; Start-Sleep -Seconds $falta }
            }
        }
    }
    $destino = "deploy@$IPServidor"
    $remoto = 't117-verificacion-' + $sesion
    # IMPORTANTE: conservar en Oracle la jerarquía del ZIP, NO copiar los tests planos.
    # Las pruebas de distribución inspeccionan lanzadores relativos a la raíz del paquete.
    $remotoMedidor = "$remoto/05_pro/PruebaApiANH/MedicionANH"
    $raizPaquete = (Resolve-Path (Join-Path $base '..\..\..')).Path
    $lanzadorRaiz = Join-Path $raizPaquete 'EJECUTAR_EN_WINDOWS.cmd'
    $lanzadorOracle = Join-Path $raizPaquete 'COMPROBAR_ORACLE_WINDOWS.cmd'
    foreach ($lanzador in @($lanzadorRaiz, $lanzadorOracle)) {
        if (-not (Test-Path -LiteralPath $lanzador)) {
            throw "Falta lanzador del paquete: $lanzador. Extrae TODO el ZIP V3."
        }
    }
    Write-Host "== Oracle Cloud: ejecutando tests sintéticos $remoto ==" -ForegroundColor Cyan
    & ssh -o BatchMode=yes -o ConnectTimeout=15 -i $LlaveSSH $destino "mkdir -p ~/$remotoMedidor"
    if ($LASTEXITCODE -ne 0) { throw 'Fallo de SSH al crear directorios remotos.' }
    # Lanzadores de la RAÍZ del paquete: los necesita test_07 en Oracle.
    & scp -q -o BatchMode=yes -i $LlaveSSH $lanzadorRaiz $lanzadorOracle "${destino}:~/$remoto/"
    if ($LASTEXITCODE -ne 0) { throw 'Fallo SCP al copiar lanzadores raíz.' }
    # Directorio MedicionANH: JAR, scripts, tests, Java y lanzadores internos.
    $archivosMedidor = @('medicion-anh.jar', 'EJECUTAR_PRUEBAS_WINDOWS.cmd', 'EJECUTAR_PRUEBAS_LINUX.sh')
    foreach ($nombre in $archivosMedidor) {
        $archivo = Join-Path $base $nombre
        if (-not (Test-Path -LiteralPath $archivo)) { throw "Falta archivo en MedicionANH: $nombre" }
        & scp -q -o BatchMode=yes -i $LlaveSSH $archivo "${destino}:~/$remotoMedidor/"
        if ($LASTEXITCODE -ne 0) { throw "Fallo SCP al copiar $nombre." }
    }
    foreach ($directorio in @('tests', 'scripts', 'java')) {
        $ruta = Join-Path $base $directorio
        if (-not (Test-Path -LiteralPath $ruta)) { throw "Falta directorio: $directorio" }
        & scp -q -o BatchMode=yes -r -i $LlaveSSH $ruta "${destino}:~/$remotoMedidor/"
        if ($LASTEXITCODE -ne 0) { throw "Fallo SCP al copiar $directorio." }
    }
    $mode = if ($ConsultaRealOracle) { '--consulta-real' } else { '--solo-simulacion' }
    & ssh -o BatchMode=yes -o ConnectTimeout=15 -o ServerAliveInterval=30 -i $LlaveSSH $destino "cd ~/$remotoMedidor && bash scripts/oracle_prueba_corta.sh $mode"
    if ($LASTEXITCODE -ne 0) { throw 'Pruebas de Oracle con errores; no se pudieron validar remotamente.' }
    & scp -q -o BatchMode=yes -i $LlaveSSH "${destino}:~/$remotoMedidor/pruebas-funcionales-oracle.txt" (Join-Path $carpetaAnalisis 'PRUEBAS_ORACLE.txt')
    if ($LASTEXITCODE -ne 0) { throw 'No se pudo bajar informe de Oracle.' }
    Write-Host "Oracle: pruebas funcionales correctas. Informe en $carpetaAnalisis" -ForegroundColor Green
    if ($ConsultaRealOracle) {
        $oracleData = Join-Path $carpetaAnalisis 'datos-oracle-corto'
        & scp -q -o BatchMode=yes -r -i $LlaveSSH "${destino}:~/$remotoMedidor/datos-oracle-corto" $oracleData
        if ($LASTEXITCODE -ne 0) { throw 'No se pudieron recuperar datos de la ronda real Oracle.' }
        & scp -q -o BatchMode=yes -i $LlaveSSH "${destino}:~/$remotoMedidor/medicion-oracle-corto.log" (Join-Path $carpetaAnalisis 'medicion-oracle-corto.log')
        if ($LASTEXITCODE -ne 0) { throw 'No se pudo descargar log Oracle.' }
        $oracleCSV = Join-Path $oracleData 'medicion-oracle.csv'
        if (-not (Test-Path $oracleCSV)) { throw "Falta $oracleCSV tras SCP." }
        if (Test-Path $localCSV) {
            & python .\scripts\diagnostico_corto.py --local $DatosLocal --oracle $oracleData --salida (Join-Path $carpetaAnalisis 'COMPARACION_LOCAL_ORACLE')
            if ($LASTEXITCODE -ne 0) { throw 'Fallo diagnóstico conjunto de datos reales.' }
        } else {
            Write-Warning 'No hay CSV locales previos: se realizó verificación Oracle sin comparación de latencias reales.'
        }
    }
    Write-Host 'TERMINADO. Pruebas de simulación completas en ambos entornos.' -ForegroundColor Green
} finally {
    Pop-Location
}
