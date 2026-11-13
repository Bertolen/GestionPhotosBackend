<#
.SYNOPSIS
    Script de déploiement MANUEL de secours (si GitLab est indisponible).
.DESCRIPTION
    Construit l'image Docker pour ARM64, la transfère sur la Raspberry Pi et la déploie.
    En temps normal, la CI GitLab déploie automatiquement au push sur master (issue #15).
.EXAMPLE
    .\scripts\deploy.ps1
#>

param (
    [string]$ImageName = "gestion-photos",
    [string]$ImageTag = "latest",
    [string]$RaspberryHost = "nuestra-traviesa",
    [string]$RaspberryUser = "bertolen",
    [int]$RaspberrySshPort = 22,
    [int]$RaspberryDockerPort = 8080,
    [string]$PhotoStoragePath = "/mnt/disque/photo-storage"
)

# Même nom que le conteneur compose : le prochain déploiement CI remplacera
# ce conteneur manuel automatiquement.
$ContainerName = "gestion-photos-backend"

Write-Host "=== Déploiement manuel de GestionPhotos Backend sur la Raspberry Pi ===" -ForegroundColor Cyan

# Charger les variables d'environnement si .env existe
if (Test-Path ".env") {
    Write-Host "Chargement de la configuration depuis .env..." -ForegroundColor Gray
    $envContent = Get-Content ".env" -Raw
    $envVars = @{}
    $envContent -split "`n" | ForEach-Object {
        if ($_ -match "^([^=]+)=(.+)$") {
            $key = $matches[1].Trim()
            $value = $matches[2].Trim()
            $envVars[$key] = $value
        }
    }

    if ($envVars.ContainsKey("IMAGE_NAME")) { $ImageName = $envVars["IMAGE_NAME"] }
    if ($envVars.ContainsKey("IMAGE_TAG")) { $ImageTag = $envVars["IMAGE_TAG"] }
    if ($envVars.ContainsKey("RASPBERRY_HOST")) { $RaspberryHost = $envVars["RASPBERRY_HOST"] }
    if ($envVars.ContainsKey("RASPBERRY_USER")) { $RaspberryUser = $envVars["RASPBERRY_USER"] }
    if ($envVars.ContainsKey("RASPBERRY_SSH_PORT")) { $RaspberrySshPort = [int]$envVars["RASPBERRY_SSH_PORT"] }
    if ($envVars.ContainsKey("RASPBERRY_DOCKER_PORT")) { $RaspberryDockerPort = [int]$envVars["RASPBERRY_DOCKER_PORT"] }
    if ($envVars.ContainsKey("PHOTO_STORAGE_PATH")) { $PhotoStoragePath = $envVars["PHOTO_STORAGE_PATH"] }
}

$FullImageName = "$ImageName`:$ImageTag"
$TarFile = "$ImageName`-$ImageTag.tar"

Write-Host "Image: $FullImageName" -ForegroundColor Yellow
Write-Host "Raspberry Pi: $RaspberryUser@$RaspberryHost`:$RaspberrySshPort" -ForegroundColor Yellow
Write-Host "Port de déploiement: $RaspberryDockerPort" -ForegroundColor Yellow
Write-Host "Stockage photos: $PhotoStoragePath" -ForegroundColor Yellow

# Étape 1: Construire l'image pour ARM64
Write-Host "`n=== Construction de l'image Docker pour ARM64 ===" -ForegroundColor Green
docker build --platform linux/arm64 -t $FullImageName .
if ($LASTEXITCODE -ne 0) {
    Write-Host "ERREUR: Build Docker échoué" -ForegroundColor Red
    exit 1
}

# Étape 2: Sauvegarder l'image dans un fichier tar
Write-Host "`n=== Sauvegarde de l'image ===" -ForegroundColor Green
docker save -o $TarFile $FullImageName
if ($LASTEXITCODE -ne 0) {
    Write-Host "ERREUR: Sauvegarde Docker échouée" -ForegroundColor Red
    exit 1
}

# Étape 3: Transférer l'image vers la Raspberry Pi
Write-Host "`n=== Transfert vers Raspberry Pi ===" -ForegroundColor Green
if (Get-Command scp -ErrorAction SilentlyContinue) {
    scp -P $RaspberrySshPort $TarFile "${RaspberryUser}@${RaspberryHost}:/tmp/"
} else {
    Write-Host "ERREUR: SCP non trouvé. Installez OpenSSH Client (Fonctionnalité Windows)" -ForegroundColor Red
    exit 1
}
if ($LASTEXITCODE -ne 0) {
    Write-Host "ERREUR: Transfert SCP échoué" -ForegroundColor Red
    Write-Host "Vous pouvez copier manuellement le fichier $TarFile vers /tmp/ sur la Raspberry" -ForegroundColor Yellow
    exit 1
}

# Étape 4: Charger et démarrer sur la Raspberry Pi
Write-Host "`n=== Déploiement sur Raspberry Pi ===" -ForegroundColor Green
$sshCommand = @"
set -e
echo "Chargement de l'image Docker..."
docker load < /tmp/$TarFile

echo "Arrêt du container existant..."
docker stop $ContainerName 2>/dev/null || true
docker rm $ContainerName 2>/dev/null || true

echo "Démarrage du nouveau container..."
docker run -d \
        --name $ContainerName \
        -p ${RaspberryDockerPort}:8080 \
        -e APP_STORAGE_PATH=/data/photos \
        -v ${PhotoStoragePath}:/data/photos \
        --restart unless-stopped \
        $FullImageName

rm /tmp/$TarFile

echo "Vérification que le backend répond..."
# Grep volontairement ASCII : l'encodage des accents transmis en argument a
# ssh.exe n'est pas garanti depuis Windows. Le job deploy CI verifie lui le
# message complet renvoye par /api/photos/status.
ok=""
for i in 1 2 3 4 5 6 7 8 9 10; do
    if curl -fsS --max-time 10 http://localhost:${RaspberryDockerPort}/api/photos/status -o /tmp/smoke-backend.txt 2>/dev/null; then ok=1; break; fi
    echo "Attente du backend (essai `$i/10)..."
    sleep 5
done
if [ -z "`$ok" ]; then
    echo "ATTENTION: pas de réponse HTTP sur le port ${RaspberryDockerPort}."
    echo "Vérifiez: docker logs $ContainerName"
    exit 1
fi
if ! grep -q 'Service Photo est op' /tmp/smoke-backend.txt; then
    echo "ATTENTION: le backend ne répond pas le message attendu sur /api/photos/status."
    echo "Vérifiez: docker logs $ContainerName"
    exit 1
fi
echo "Container démarré avec succès !"
echo "Accès: http://${RaspberryHost}:${RaspberryDockerPort}"
"@

if (Get-Command ssh -ErrorAction SilentlyContinue) {
    ssh -p $RaspberrySshPort "${RaspberryUser}@${RaspberryHost}" $sshCommand
} else {
    Write-Host "ERREUR: SSH non trouvé" -ForegroundColor Red
    exit 1
}
if ($LASTEXITCODE -ne 0) {
    Write-Host "ERREUR: Déploiement SSH échoué" -ForegroundColor Red
    exit 1
}

# Étape 5: Nettoyage local
Write-Host "`n=== Nettoyage local ===" -ForegroundColor Green
Remove-Item $TarFile -Force -ErrorAction SilentlyContinue

Write-Host "`n=== Déploiement terminé avec succès ! ===" -ForegroundColor Green
Write-Host "Application disponible sur: http://${RaspberryHost}:${RaspberryDockerPort}" -ForegroundColor Cyan
Write-Host ""
Write-Host "Note: au prochain déploiement CI, le conteneur manuel sera remplacé" -ForegroundColor Gray
Write-Host "automatiquement par le conteneur docker compose." -ForegroundColor Gray
