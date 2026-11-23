# Verificación NO destructiva del contrato GET /surtidores con un JWT de conductor.
# Requiere backend YA desplegado, datos ANH vigentes y vínculos reales aprobados en la BD.
# Uso: .\scripts\Probar-GET-Surtidores-ANH.ps1 -BaseUrl 'https://SERVIDOR_REAL'
param(
    [Parameter(Mandatory=$true)][uri]$BaseUrl,
    [double]$Latitud = -21.5355,
    [double]$Longitud = -64.7296
)
Set-StrictMode -Version Latest
$ErrorActionPreference = 'Stop'

if ($BaseUrl.Scheme -ne 'https' -and $BaseUrl.Host -notin @('localhost','127.0.0.1')) {
    throw 'El backend debe usar HTTPS salvo localhost/127.0.0.1 en pruebas.'
}
$jwtSecure = Read-Host 'Introduce el JWT de un conductor autorizado' -AsSecureString
$bstr = [System.Runtime.InteropServices.Marshal]::SecureStringToBSTR($jwtSecure)
try { $jwt = [System.Runtime.InteropServices.Marshal]::PtrToStringBSTR($bstr) }
finally { [System.Runtime.InteropServices.Marshal]::ZeroFreeBSTR($bstr) }
if ([string]::IsNullOrWhiteSpace($jwt)) { throw 'No se recibió JWT.' }
$separador = if ($BaseUrl.AbsoluteUri.EndsWith('/')) { '' } else { '/' }
$uri = $BaseUrl.AbsoluteUri + $separador + 'surtidores?latitud=' + $Latitud.ToString([System.Globalization.CultureInfo]::InvariantCulture) + '&longitud=' + $Longitud.ToString([System.Globalization.CultureInfo]::InvariantCulture)
$headers = @{ Authorization = "Bearer $jwt" }
try { $r = Invoke-RestMethod -Uri $uri -Headers $headers -Method GET -TimeoutSec 20 }
finally { Remove-Variable jwt,jwtSecure,headers -ErrorAction SilentlyContinue }
if ($null -eq $r -or $null -eq $r.surtidores) { throw 'Respuesta inválida: falta el arreglo surtidores.' }

$anh = @()
foreach ($s in $r.surtidores) {
    foreach ($c in $s.combustibles) {
        if ($c.origen -eq 'ANH') {
            if ($null -eq $c.litros -or $null -eq $c.estadoAnh -or $null -eq $c.consultadoEn) {
                throw "Campo obligatorio de ANH ausente en el surtidor $($s.nombre)"
            }
            if ([decimal]$c.litros -lt 0) { throw "Litros negativos en $($s.nombre)" }
            $anh += [pscustomobject]@{ Surtidor=$s.nombre; Tipo=$c.tipo; Litros=$c.litros; EstadoAnh=$c.estadoAnh; ConsultadoEn=$c.consultadoEn }
        }
    }
}
Write-Host "Surtidores en respuesta: $(@($r.surtidores).Count)"
Write-Host "Estados con origen ANH visibles: $($anh.Count)"
$anh | Format-Table -AutoSize
if ($anh.Count -eq 0) {
    Write-Warning 'No se observan estados ANH vigentes en esta respuesta. No demuestra fallo: pueden estar vencidos o superados por otra fuente; revisar consultas programadas, BD, vínculos y fechas.'
    exit 2
}
Write-Host 'Contrato GET /surtidores con datos ANH: campos presentes y con valores básicos válidos.'
exit 0
