param(
    [Parameter(Mandatory=$true)][string]$StoreFile,
    [Parameter(Mandatory=$true)][string]$KeyAlias,
    [string]$AdminApiUrl='',
    [switch]$EnableAds,
    [string]$AdmobAppId='',
    [switch]$AdsPolicyReviewed
)
$ErrorActionPreference='Stop'
if($EnableAds -and (-not $AdminApiUrl -or $AdmobAppId -notmatch '^ca-app-pub-\d{16}~\d{10}$' -or -not $AdsPolicyReviewed)) {
    throw 'The ads edition requires AdminApiUrl, a real AdmobAppId, and AdsPolicyReviewed after reviewing consent, audience and Play disclosures.'
}
if($AdminApiUrl) {
    [uri]$lumenApiUri=$null
    if(-not [uri]::TryCreate($AdminApiUrl,[UriKind]::Absolute,[ref]$lumenApiUri) -or $lumenApiUri.Scheme -ne 'https' -or $lumenApiUri.Host -in @('localhost','127.0.0.1') -or $lumenApiUri.AbsolutePath -ne '/' -or $lumenApiUri.Query -or $lumenApiUri.Fragment) {
        throw 'AdminApiUrl must be a public HTTPS origin, such as https://admin.your-domain.com, without /api or another path.'
    }
}
$workspaceRoot=[System.IO.Path]::GetFullPath((Join-Path $PSScriptRoot '..'))
$keystorePath=(Resolve-Path -LiteralPath $StoreFile).Path
if(!(Test-Path -LiteralPath $keystorePath -PathType Leaf)) { throw 'An existing upload keystore is required. This script does not generate one.' }
$names=@('LUMEN_UPLOAD_STORE_FILE','LUMEN_UPLOAD_STORE_PASSWORD','LUMEN_UPLOAD_KEY_ALIAS','LUMEN_UPLOAD_KEY_PASSWORD')
$previous=@{}
foreach($name in $names) { $previous[$name]=[Environment]::GetEnvironmentVariable($name,'Process') }
function Convert-Secret([Security.SecureString]$secret) {
    $pointer=[Runtime.InteropServices.Marshal]::SecureStringToBSTR($secret)
    try { [Runtime.InteropServices.Marshal]::PtrToStringBSTR($pointer) }
    finally { [Runtime.InteropServices.Marshal]::ZeroFreeBSTR($pointer) }
}
$storeSecret=$null; $keySecret=$null
Push-Location -LiteralPath $workspaceRoot
try {
    $storeSecret=Read-Host 'Upload keystore password (kept local)' -AsSecureString
    $keySecret=Read-Host 'Upload key password (kept local)' -AsSecureString
    [Environment]::SetEnvironmentVariable('LUMEN_UPLOAD_STORE_FILE',$keystorePath,'Process')
    [Environment]::SetEnvironmentVariable('LUMEN_UPLOAD_KEY_ALIAS',$KeyAlias,'Process')
    [Environment]::SetEnvironmentVariable('LUMEN_UPLOAD_STORE_PASSWORD',(Convert-Secret $storeSecret),'Process')
    [Environment]::SetEnvironmentVariable('LUMEN_UPLOAD_KEY_PASSWORD',(Convert-Secret $keySecret),'Process')
    $lumenBuildArgs=@(':app:verifyPlayRelease',':app:preparePlayDocuments',':app:bundleRelease','-PplayRelease=true','--no-daemon')
    if($AdminApiUrl) { $lumenBuildArgs+="-PadminApiUrl=$($AdminApiUrl.TrimEnd('/'))" }
    if($EnableAds) { $lumenBuildArgs+=@('-PenableAds=true',"-PadmobAppId=$AdmobAppId",'-PadsPolicyReviewed=true') }
    & (Join-Path $workspaceRoot 'gradlew.bat') @lumenBuildArgs
    if($LASTEXITCODE -ne 0) { throw 'Release validation or build failed. Review the non-secret Gradle diagnostics.' }
    Write-Output 'Signed local bundle built. Complete device testing, hosted policy verification and Console declarations before upload.'
} finally {
    foreach($name in $names) { [Environment]::SetEnvironmentVariable($name,$previous[$name],'Process') }
    if($null -ne $storeSecret) { $storeSecret.Dispose() }
    if($null -ne $keySecret) { $keySecret.Dispose() }
    Pop-Location
}
