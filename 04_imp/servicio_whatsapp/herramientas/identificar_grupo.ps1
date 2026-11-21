#requires -Version 5.1
$ErrorActionPreference = 'Stop'
# El script vive en 04_imp\servicio_whatsapp\herramientas; el servicio es la carpeta de arriba.
$Servicio = [IO.Path]::GetFullPath((Join-Path $PSScriptRoot '..'))
$Identificador = Join-Path $PSScriptRoot 'descubrir_jid_grupo.mjs'
$Privado = Join-Path $env:LOCALAPPDATA 'ChuroViajeIssue59'
New-Item -ItemType Directory -Path $Privado -Force | Out-Null
$UsuarioWindows = [System.Security.Principal.WindowsIdentity]::GetCurrent().Name
& icacls.exe $Privado '/inheritance:r' '/grant:r' "$($UsuarioWindows):(OI)(CI)F" 'SYSTEM:(OI)(CI)F' | Out-Null
if ($LASTEXITCODE -ne 0) { throw 'No se pudieron restringir los permisos de la sesion privada.' }
$env:WHATSAPP_SESSION_PATH = Join-Path $Privado 'sesion.sqlite'
if (-not (Test-Path (Join-Path $Servicio 'node_modules\@whiskeysockets\baileys'))) {
    throw 'Antes ejecuta npm install en 04_imp\servicio_whatsapp para instalar Baileys.'
}
Write-Host 'Este auxiliar muestra el ID tecnico del grupo, NUNCA los mensajes.'
Write-Host 'Abre WhatsApp > Dispositivos vinculados > Vincular dispositivo si aparece QR.'
Write-Host 'Envía un mensaje ficticio en el grupo autorizado y copia su JID localmente.'
Write-Host 'No ejecutes simultáneamente el servicio completo sobre la misma sesión.'
& node $Identificador
if ($LASTEXITCODE -ne 0) { throw 'El diagnóstico de JID terminó con error.' }
