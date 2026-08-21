# Compila r-Info y arma los artefactos de distribución en build/.
#
#   .\build.ps1           compila y arma build\r-info.jar
#   .\build.ps1 -Nativo   además arma la aplicación de Windows con su propio
#                         runtime y la comprime, para quien no tenga Java
#
# Necesita un JDK 21 o superior en el PATH (jpackage viene con el JDK).

param([switch]$Nativo)

$ErrorActionPreference = "Stop"
$version = "1.0.0"

Remove-Item -Recurse -Force build -ErrorAction SilentlyContinue
New-Item -ItemType Directory -Force build\clases | Out-Null

# --release 21 y no la versión del JDK que compila: si no, el .class exige esa
# misma versión en la máquina del que lo corre.
$fuentes = (Get-ChildItem -Recurse src -Filter *.java).FullName
& javac --release 21 -encoding UTF-8 -d build\clases $fuentes
if (-not $?) { throw "falló la compilación" }

@"
Main-Class: rinfo.Rinfo
Implementation-Title: r-Info
Implementation-Version: $version
"@ | Out-File -Encoding ascii build\manifest.txt

& jar --create --file build\r-info.jar --manifest build\manifest.txt -C build\clases .
Write-Host "listo: build\r-info.jar"

if ($Nativo) {
    New-Item -ItemType Directory -Force build\entrada | Out-Null
    Copy-Item build\r-info.jar build\entrada\

    & jpackage --type app-image --name r-Info --input build\entrada `
        --main-jar r-info.jar --main-class rinfo.Rinfo `
        --add-modules java.base,java.desktop `
        --app-version $version --vendor "Theo Cervera" --dest build\app `
        --jlink-options "--strip-native-commands --strip-debug --no-man-pages --no-header-files --compress=zip-6"

    Copy-Item -Recurse ejemplos build\app\r-Info\ejemplos
    Compress-Archive -Path build\app\r-Info -DestinationPath "build\r-Info-$version-windows.zip" -Force
    Write-Host "listo: build\r-Info-$version-windows.zip"
}
