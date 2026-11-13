#!/bin/bash

# Script de déploiement MANUEL de secours (si GitLab est indisponible).
# En temps normal, la CI GitLab déploie automatiquement au push sur master (issue #15).
#
# Usage: ./scripts/deploy.sh
#
# Valeurs réelles de la prod : frontend sur 80, backend Spring Boot sur 8080,
# hostname nuestra-traviesa (résolu par le DNS de la box), SSH bertolen@nuestra-traviesa.

set -e

echo "=== Déploiement manuel de GestionPhotos Backend sur la Raspberry Pi ==="

# Chargement des variables d'environnement
if [ -f .env ]; then
    source .env
fi

# Variables par défaut (peuvent être écrasées par .env)
IMAGE_NAME=${IMAGE_NAME:-gestion-photos}
IMAGE_TAG=${IMAGE_TAG:-latest}
RASPBERRY_HOST=${RASPBERRY_HOST:-nuestra-traviesa}
RASPBERRY_USER=${RASPBERRY_USER:-bertolen}
RASPBERRY_SSH_PORT=${RASPBERRY_SSH_PORT:-22}
RASPBERRY_DOCKER_PORT=${RASPBERRY_DOCKER_PORT:-8080}
PHOTO_STORAGE_PATH=${PHOTO_STORAGE_PATH:-/mnt/disque/photo-storage}

# Même nom que le conteneur compose : le prochain déploiement CI remplacera
# ce conteneur manuel automatiquement.
CONTAINER_NAME=gestion-photos-backend

FULL_IMAGE_NAME="${IMAGE_NAME}:${IMAGE_TAG}"
TAR_FILE="${IMAGE_NAME}-${IMAGE_TAG}.tar"

echo "Image: $FULL_IMAGE_NAME"
echo "Raspberry Pi: ${RASPBERRY_USER}@${RASPBERRY_HOST}:${RASPBERRY_SSH_PORT}"
echo "Port de déploiement: ${RASPBERRY_DOCKER_PORT}"
echo "Stockage photos: ${PHOTO_STORAGE_PATH}"

# Étape 1: Construire l'image pour ARM64
echo ""
echo "=== Construction de l'image Docker pour ARM64 ==="
docker build --platform linux/arm64 -t "$FULL_IMAGE_NAME" .

# Étape 2: Sauvegarder l'image dans un fichier tar
echo ""
echo "=== Sauvegarde de l'image ==="
docker save "$FULL_IMAGE_NAME" > "$TAR_FILE"

# Étape 3: Transférer l'image vers la Raspberry Pi
echo ""
echo "=== Transfert vers Raspberry Pi ==="
scp -P "$RASPBERRY_SSH_PORT" "$TAR_FILE" "${RASPBERRY_USER}@${RASPBERRY_HOST}:/tmp/"

# Étape 4: Charger et démarrer sur la Raspberry Pi
echo ""
echo "=== Déploiement sur Raspberry Pi ==="
ssh -p "$RASPBERRY_SSH_PORT" "${RASPBERRY_USER}@${RASPBERRY_HOST}" << EOF
    set -e
    echo "Chargement de l'image Docker..."
    docker load < /tmp/${TAR_FILE}

    echo "Arrêt du container existant..."
    docker stop ${CONTAINER_NAME} 2>/dev/null || true
    docker rm ${CONTAINER_NAME} 2>/dev/null || true

    echo "Démarrage du nouveau container..."
    docker run -d \
        --name ${CONTAINER_NAME} \
        -p ${RASPBERRY_DOCKER_PORT}:8080 \
        -e APP_STORAGE_PATH=/data/photos \
        -v ${PHOTO_STORAGE_PATH}:/data/photos \
        --restart unless-stopped \
        ${FULL_IMAGE_NAME}

    rm /tmp/${TAR_FILE}

    echo "Vérification que le backend répond..."
    # Fichier unique via mktemp : un chemin fixe peut appartenir à un autre
    # utilisateur (root) et faire échouer curl en erreur d'écriture (exit 23).
    ok=""
    SMOKE_FILE="\$(mktemp /tmp/smoke-backend.XXXXXX)"
    for i in 1 2 3 4 5 6 7 8 9 10; do
        if curl -fsS --max-time 10 http://localhost:${RASPBERRY_DOCKER_PORT}/api/photos/status -o "\$SMOKE_FILE" 2>/dev/null; then ok=1; break; fi
        echo "Attente du backend (essai \$i/10)..."
        sleep 5
    done
    if [ -z "\$ok" ]; then
        echo "ATTENTION: pas de réponse HTTP sur le port ${RASPBERRY_DOCKER_PORT}."
        echo "Vérifiez: docker logs ${CONTAINER_NAME}"
        rm -f "\$SMOKE_FILE"
        exit 1
    fi
    if ! grep -q 'Service Photo est opérationnel' "\$SMOKE_FILE"; then
        echo "ATTENTION: le backend ne répond pas le message attendu sur /api/photos/status."
        echo "Vérifiez: docker logs ${CONTAINER_NAME}"
        rm -f "\$SMOKE_FILE"
        exit 1
    fi
    rm -f "\$SMOKE_FILE"
    echo "Container démarré avec succès !"
    echo "Accès: http://${RASPBERRY_HOST}:${RASPBERRY_DOCKER_PORT}"
EOF

# Étape 5: Nettoyage local
echo ""
echo "=== Nettoyage local ==="
rm "$TAR_FILE"

echo ""
echo "=== Déploiement terminé avec succès ! ==="
echo "Application disponible sur: http://${RASPBERRY_HOST}:${RASPBERRY_DOCKER_PORT}"
echo ""
echo "Note: au prochain déploiement CI, le conteneur manuel sera remplacé"
echo "automatiquement par le conteneur docker compose."
