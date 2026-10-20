$ErrorActionPreference='Stop'
Push-Location $PSScriptRoot
try {
    if(-not (Test-Path -LiteralPath (Join-Path $PSScriptRoot 'data/lumen.sqlite'))) {
        $env:LUMEN_ADMIN_EMAIL=Read-Host 'Local administrator email'
        $lumenSecret=Read-Host 'Local administrator password (8+ characters; use a stronger one for hosting)' -AsSecureString
        $lumenPointer=[Runtime.InteropServices.Marshal]::SecureStringToBSTR($lumenSecret)
        try { $env:LUMEN_ADMIN_PASSWORD=[Runtime.InteropServices.Marshal]::PtrToStringBSTR($lumenPointer) }
        finally { [Runtime.InteropServices.Marshal]::ZeroFreeBSTR($lumenPointer) }
    }
    node (Join-Path $PSScriptRoot 'server.mjs')
} finally {
    Remove-Item Env:LUMEN_ADMIN_PASSWORD -ErrorAction SilentlyContinue
    Pop-Location
}
