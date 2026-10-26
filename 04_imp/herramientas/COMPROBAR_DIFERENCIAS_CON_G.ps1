param([Parameter(Mandatory=$true)][string]$ProyectoG)
$ErrorActionPreference = 'Stop'
$raiz = (Resolve-Path -LiteralPath $ProyectoG).Path
$destino = (Resolve-Path -LiteralPath (Join-Path $PSScriptRoot '..')).Path
Write-Host "=== COMPARACION SOLO LECTURA (sin Git, sin reemplazos) ==="
$objetivos = @(
 'backend_java\pom.xml',
 'backend_java\src\main\java\com\churoviaje\backend\ChuroViajeBackendApplication.java',
 'backend_java\src\test\java\bo\edu\ucb\churoviaje\infraestructura\http\T089_ManejadoresMvcTest.java',
 'backend_java\src\test\java\bo\edu\ucb\churoviaje\infraestructura\persistencia\CatalogosJdbcPostgisTest.java',
 'backend_java\src\test\java\bo\edu\ucb\churoviaje\integracion\T089ArranqueAisladoTest.java',
 'evidencias\T089_VALIDACION_16_PRUEBAS.txt',
 'integraciones\ANH_T115\salida_tarija\20261024T181654_139720Z\datos_tarija.json'
)
foreach($r in $objetivos){
 $a=Join-Path $raiz $r;$b=Join-Path $destino $r
 $estado = if(-not (Test-Path -LiteralPath $a)){ 'NO EXISTE EN G' }
           elseif(-not (Test-Path -LiteralPath $b)){ 'SOLO EN G (NO INCLUIDO EN RAR)' }
           elseif((Get-FileHash -LiteralPath $a).Hash -eq (Get-FileHash -LiteralPath $b).Hash){ 'IGUALES' }
           else{ 'DISTINTOS' }
 Write-Host "$estado | $r"
}
Write-Host 'Ningún archivo fue modificado.'
