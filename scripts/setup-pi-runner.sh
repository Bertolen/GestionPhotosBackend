#!/usr/bin/env bash
#
# Wizard d'installation de la chaîne CI/CD GestionPhotos Backend sur la Raspberry Pi.
#
# À exécuter SUR LA PI, en SSH, par le propriétaire (issue #15) :
#   ssh bertolen@nuestra-traviesa
#   git clone https://gitlab.com/nuestra-traviesa/GestionPhotosBackend.git
#   cd GestionPhotosBackend && sudo bash scripts/setup-pi-runner.sh
#
# Chaque étape vérifie ses prérequis avant de continuer et échoue avec un
# message actionnable ; le wizard reprend là où il s'était arrêté.
# Le disque des photos par défaut est /dev/sda1 : pour un autre disque,
# relancez avec « sudo DISK_DEVICE=/dev/sdb bash scripts/setup-pi-runner.sh ».
#
# Le wizard n'installe QUE le runner de déploiement : les builds et tests
# tournent sur les runners partagés GitLab.com, la Pi n'exécute que le job
# « deploy » (pull d'image + compose up + smoke test).

set -euo pipefail

PROJECT_WEB_URL="https://gitlab.com/nuestra-traviesa/GestionPhotosBackend"
PROJECT_API_PATH="nuestra-traviesa%2FGestionPhotosBackend"
FRONTEND_WEB_URL="https://gitlab.com/nuestra-traviesa/GestionPhotosFrontend"
GROUP_RUNNERS_URL="https://gitlab.com/nuestra-traviesa/-/settings/ci_cd"
RUNNER_TAG="rpi"
NEW_RUNNER_NAME="gestion-photos-group-pi"
ENV_DIR="/home/pi/gestion-photos"
ENV_FILE="$ENV_DIR/.env"
PHOTO_STORAGE_PATH_DEFAULT="/mnt/disque/photo-storage"
DISK_DEVICE="${DISK_DEVICE:-/dev/sda1}"
DISK_MOUNT="${DISK_MOUNT:-/mnt/disque}"
CONTAINER_NAME="gestion-photos-backend"
SMOKE_URL="http://localhost:8080/api/photos/status"
CONFIG_FILE="/etc/gitlab-runner/config.toml"

info() { echo -e "\e[1;34m[INFO]\e[0m $*"; }
ok()   { echo -e "\e[1;32m[OK]\e[0m $*"; }
warn() { echo -e "\e[1;33m[ATTENTION]\e[0m $*"; }
fail() { echo -e "\e[1;31m[ERREUR]\e[0m $*" >&2; echo -e "\e[1;31mLe wizard s'est arrêté. Corrigez le problème ci-dessus puis relancez-le : il reprendra là où il en était.\e[0m" >&2; exit 1; }

confirm() {
    local reponse
    read -rp "$1 (o/N) : " reponse
    case "$reponse" in [oO]|[oO]ui*) return 0 ;; *) return 1 ;; esac
}

# Paires « nom|token » des entrées [[runners]] du config.toml, dans l'ordre.
# Le token est l'identifiant fiable d'un runner : le nom peut être dupliqué
# entre entrées (vécu : 3 entrées « nuestra-traviesa » après des relances du
# wizard), et « gitlab-runner unregister --name » ne retire que la première
# correspondance. Le nom peut être vide (bloc incomplet édité à la main).
runner_entries() {
    $SUDO awk '
        /^\[\[runners\]\]/ { name = "" }
        /^ *name *= *"/ { line = $0; sub(/^ *name *= *"/, "", line); sub(/"$/, "", line); name = line }
        /^ *token *= *"/ { line = $0; sub(/^ *token *= *"/, "", line); sub(/"$/, "", line); print name "|" line }
    ' "$CONFIG_FILE" 2>/dev/null || true
}

trap 'echo -e "\e[1;31m[ERREUR]\e[0m Échec inattendu — voir le message ci-dessus." >&2' ERR

echo "============================================================"
echo "  GestionPhotos Backend — installation de la chaîne CI/CD"
echo "  sur la Pi (déploiement automatique au push sur master, issue #15)"
echo "============================================================"
echo

# --- Étape 1 : prérequis ---------------------------------------------------

info "Étape 1/8 : vérification des prérequis..."

[ "$(uname -m)" = "aarch64" ] || fail "L'OS doit être un Raspberry Pi OS 64 bits (aarch64). OS actuel : $(uname -m)."

command -v curl >/dev/null 2>&1 || fail "curl est manquant : sudo apt-get update && sudo apt-get install -y curl"

if [ "$(id -u)" -eq 0 ]; then
    SUDO=""
else
    command -v sudo >/dev/null 2>&1 || fail "sudo est manquant : exécutez ce wizard en root ou installez sudo."
    SUDO="sudo"
fi

$SUDO docker info >/dev/null 2>&1 || fail "Docker ne répond pas. Installez-le : curl -fsSL https://get.docker.com | sh puis relancez."
ok "Docker est installé et répond."

$SUDO docker compose version >/dev/null 2>&1 || fail "Le plugin docker compose est manquant : sudo apt-get install -y docker-compose-plugin"
ok "Le plugin docker compose est présent."

# Disque externe des photos : il doit être monté AVANT tout le reste (fstab à
# l'étape 4, mais le montage manuel suffit à passer cette vérification).
if ! mountpoint -q "$DISK_MOUNT"; then
    [ -b "$DISK_DEVICE" ] || fail "Le disque externe $DISK_DEVICE est introuvable. Identifiez le disque des photos avec lsblk, puis relancez : sudo DISK_DEVICE=/dev/sdX bash scripts/setup-pi-runner.sh"
    $SUDO mkdir -p "$DISK_MOUNT"
    $SUDO mount "$DISK_DEVICE" "$DISK_MOUNT" \
        || fail "Impossible de monter $DISK_DEVICE sur $DISK_MOUNT (essayez : sudo mount $DISK_DEVICE $DISK_MOUNT)."
fi
mountpoint -q "$DISK_MOUNT" || fail "$DISK_MOUNT n'est pas monté : le déploiement CI refuserait de tourner sans lui."
ok "Le disque externe est monté sur $DISK_MOUNT."

# --- Étape 2 : migration vers le group runner (ADR 0001) -------------------

info "Étape 2/8 : migration vers le group runner « nuestra-traviesa » (ADR 0001)..."

if command -v gitlab-runner >/dev/null 2>&1; then
    ok "gitlab-runner est déjà installé : $(gitlab-runner --version 2>/dev/null | head -n1)"
else
    info "Ajout du dépôt officiel gitlab-runner..."
    curl -L "https://packages.gitlab.com/install/repositories/runner/gitlab-runner/script.deb.sh" | $SUDO bash \
        || fail "Impossible d'ajouter le dépôt gitlab-runner. Vérifiez la connexion internet."
    $SUDO apt-get install -y gitlab-runner \
        || fail "Échec de l'installation du paquet gitlab-runner."
    ok "gitlab-runner installé : $(gitlab-runner --version 2>/dev/null | head -n1)"
fi

$SUDO systemctl enable --now gitlab-runner >/dev/null 2>&1 || true
$SUDO systemctl is-active gitlab-runner >/dev/null || fail "Le service gitlab-runner n'est pas actif : sudo systemctl status gitlab-runner"
ok "Le service gitlab-runner est actif."

if $SUDO gitlab-runner list 2>/dev/null | grep -q "$NEW_RUNNER_NAME"; then
    ok "Le group runner est déjà enregistré sur cette Pi."
else
    echo
    info "Création du runner dans l'interface GitLab (nécessite le rôle Owner sur le groupe) :"
    echo
    echo "  1. Ouvrez : $GROUP_RUNNERS_URL (Settings > CI/CD > Runners du GROUPE nuestra-traviesa)"
    echo "  2. Cliquez sur « New group runner »."
    echo "  3. Description : $NEW_RUNNER_NAME"
    echo "  4. Tags : $RUNNER_TAG   <-- IMPORTANT : les jobs deploy sont taggés « $RUNNER_TAG »"
    echo "  5. Ne cochez PAS « Protected » (la branche master n'est pas protégée)"
    echo "  6. Ne cochez PAS « Run untagged jobs »"
    echo "  7. Créez le runner puis copiez le token affiché (il commence par glrt-)."
    echo
    read -rsp "Collez le token du runner ici (entrée cachée) : " RUNNER_TOKEN
    echo
    [ -n "$RUNNER_TOKEN" ] || fail "Token vide. Créez le runner dans l'interface puis relancez le wizard."

    # Garde-fou doublon : le register AJOUTE une entrée [[runners]] au
    # config.toml sans détecter un token déjà enregistré (vécu : le runner
    # en double après des relances du wizard). Si le token y figure déjà,
    # le runner est enregistré : pas de second register.
    if $SUDO grep -qF -- "$RUNNER_TOKEN" "$CONFIG_FILE" 2>/dev/null; then
        warn "Ce token est déjà présent dans $CONFIG_FILE : enregistrement sauté (pas de doublon)."
        ok "Le group runner est déjà enregistré sur cette Pi."
    else
        # --name fixe le nom dans le config.toml (sans lui, le register prend
        # le hostname de la Pi et le check « déjà enregistré » ci-dessus ne
        # matche jamais aux relances). Le register AJOUTE une entrée : l'ancien
        # runner du projet frontend reste enregistré pendant la coexistence
        # (repli instantané).
        $SUDO gitlab-runner register --non-interactive \
            --url "https://gitlab.com" \
            --token "$RUNNER_TOKEN" \
            --name "$NEW_RUNNER_NAME" \
            --executor shell \
            || fail "Enregistrement échoué. Vérifiez le token (un runner ne peut être enregistré qu'une fois) et la connexion à gitlab.com."

        $SUDO gitlab-runner verify >/dev/null 2>&1 \
            || fail "Le runner ne parvient pas à contacter GitLab : sudo gitlab-runner verify pour le détail."
        ok "Le group runner est enregistré et contacte GitLab."
    fi
fi

# Un job à la fois sur la Pi (file FIFO partagée avec le frontend).
if $SUDO grep -q '^concurrent' "$CONFIG_FILE"; then
    $SUDO sed -i 's/^concurrent = .*/concurrent = 1/' "$CONFIG_FILE"
else
    $SUDO sed -i '1i concurrent = 1' "$CONFIG_FILE"
fi
$SUDO systemctl restart gitlab-runner
$SUDO grep -q '^concurrent = 1' "$CONFIG_FILE" || fail "Impossible de fixer concurrent = 1 dans $CONFIG_FILE"
ok "Le runner traitera un job à la fois."

# Les jobs (shell executor) tournent sous le compte « gitlab-runner » : c'est lui
# qui doit accéder au socket Docker.
JOB_USER="gitlab-runner"
if $SUDO id "$JOB_USER" >/dev/null 2>&1; then
    if ! $SUDO id -nG "$JOB_USER" | grep -qw docker; then
        $SUDO usermod -aG docker "$JOB_USER"
        ok "Utilisateur « $JOB_USER » ajouté au groupe docker."
    else
        ok "L'utilisateur « $JOB_USER » est déjà dans le groupe docker."
    fi
    $SUDO systemctl restart gitlab-runner
else
    warn "Compte « $JOB_USER » introuvable : vérifiez manuellement que l'utilisateur des jobs appartient au groupe docker."
fi

echo
info "Pause de l'ANCIEN runner — à faire AVANT la vérification frontend :"
echo "  pendant la coexistence, les deux runners portent le tag « $RUNNER_TAG » et"
echo "  un job frontend pris par l'ancien ne prouverait rien (ADR 0001)."
echo
echo "  1. Ouvrez : $FRONTEND_WEB_URL/-/settings/ci_cd (Settings > CI/CD > Runners)"
echo "  2. « Project runners » : ancien runner du frontend (ex. gestion-photos-pi)"
echo "  3. Bouton « Pause »."
echo
confirm "L'ancien runner est en pause ?" || fail "Mettez l'ancien runner en pause puis relancez le wizard (retour arrière à tout moment : le résumer)."
ok "Ancien runner en pause."

echo
info "Vérification frontend : la chaîne existante doit être VERTE via le NOUVEAU runner."
echo
echo "  1. Déclenchez une pipeline frontend master :"
echo "     - soit depuis votre PC : git push gitlab master (repo GestionPhotosFrontend)"
echo "     - soit dans GitLab : Build > Pipelines > Run pipeline (branche master)"
echo "  2. Dans le job deploy, vérifiez qu'il a tourné sur $NEW_RUNNER_NAME."
echo "  3. Attendez la pipeline verte (le frontend doit rester servi sur le port 80)."
echo
confirm "La pipeline frontend master est verte via le nouveau runner ?" || fail "Corrigez (retour arrière : résumer l'ancien runner), puis relancez le wizard."
ok "Le group runner sert déjà le frontend : la migration est prouvée et réversible."

# --- Étape 3 : port 8080 ---------------------------------------------------

info "Étape 3/8 : conteneur existant sur le port 8080..."

EXISTING_ON_8080="$($SUDO docker ps --filter "publish=8080" --format '{{.Names}}|{{.Image}}' || true)"
if [ -n "$EXISTING_ON_8080" ]; then
    while IFS='|' read -r name image; do
        if [ "$name" = "$CONTAINER_NAME" ]; then
            if [ -n "$($SUDO docker inspect -f '{{index .Config.Labels "com.docker.compose.project"}}' "$name" 2>/dev/null || true)" ]; then
                ok "Le backend est déjà géré par docker compose."
            else
                info "Conteneur manuel « $name » détecté (démarré à la main)."
                info "Le job deploy de la CI le remplacera automatiquement au premier déploiement : rien à faire."
            fi
        else
            warn "Le conteneur « $name » ($image) occupe le port 8080."
            warn "Le premier déploiement CI échouera s'il reste là (conflit de port)."
            if confirm "L'arrêter et le supprimer maintenant ?"; then
                $SUDO docker rm -f "$name"
                ok "Conteneur « $name » supprimé."
            else
                warn "Il faudra le retirer avant le premier déploiement CI."
            fi
        fi
    done <<< "$EXISTING_ON_8080"
else
    ok "Aucun conteneur n'occupe le port 8080 (le backend manuel --rm est toléré comme absent)."
fi

# --- Étape 4 : fstab (persistance du montage du disque) ---------------------

info "Étape 4/8 : entrée fstab du disque externe..."

UUID="$($SUDO blkid -s UUID -o value "$DISK_DEVICE")"
[ -n "$UUID" ] || fail "Impossible de résoudre l'UUID de $DISK_DEVICE (vérifiez avec lsblk que c'est bien le disque des photos)."

if $SUDO grep -q "^UUID=$UUID " /etc/fstab; then
    ok "Le disque est déjà déclaré dans /etc/fstab."
else
    if $SUDO grep -qE "^[^#].*[[:space:]]$DISK_MOUNT[[:space:]]" /etc/fstab; then
        warn "Une autre entrée fstab monte déjà $DISK_MOUNT :"
        $SUDO grep -nE "^[^#].*[[:space:]]$DISK_MOUNT[[:space:]]" /etc/fstab
        confirm "La remplacer par l'entrée attendue (nofail,user) ?" || fail "Corrigez /etc/fstab à la main puis relancez le wizard."
        $SUDO sed -i "\#^[^#].*$DISK_MOUNT#d" /etc/fstab
    fi
    info "Ajout de l'entrée fstab (nofail : boot non bloqué si disque absent ; user : remontage sans sudo par le job deploy)..."
    echo "UUID=$UUID $DISK_MOUNT ext4 defaults,nofail,user 0 2" | $SUDO tee -a /etc/fstab >/dev/null
fi

$SUDO findmnt --verify || fail "findmnt --verify signale un problème dans /etc/fstab (voir ci-dessus)."
FSTAB_OPTS="$($SUDO findmnt --fstab --noheadings -o OPTIONS "$DISK_MOUNT" 2>/dev/null || true)"
echo "$FSTAB_OPTS" | grep -qw user \
    || fail "L'entrée fstab de $DISK_MOUNT n'a pas l'option « user » : le job deploy ne pourrait pas remonter le disque sans sudo. Corrigez /etc/fstab."
ok "Entrée fstab vérifiée : UUID=$UUID $DISK_MOUNT ($FSTAB_OPTS)."

# --- Étape 5 : .env partagé + répertoire de stockage ------------------------

info "Étape 5/8 : fichier .env partagé (PHOTO_STORAGE_PATH)..."

if [ -f "$ENV_FILE" ]; then
    if grep -q '^PHOTO_STORAGE_PATH=' "$ENV_FILE"; then
        ok "PHOTO_STORAGE_PATH déjà présent dans $ENV_FILE."
    else
        read -rp "Chemin des photos sur le disque (Entrée = $PHOTO_STORAGE_PATH_DEFAULT) : " PHOTO_STORAGE_PATH_ANSWER
        PHOTO_STORAGE_PATH_ANSWER="${PHOTO_STORAGE_PATH_ANSWER:-$PHOTO_STORAGE_PATH_DEFAULT}"
        printf 'PHOTO_STORAGE_PATH=%s\n' "$PHOTO_STORAGE_PATH_ANSWER" | $SUDO tee -a "$ENV_FILE" >/dev/null
        ok "PHOTO_STORAGE_PATH ajouté à $ENV_FILE sans toucher au reste (API_BASE_URL intact)."
    fi
else
    warn "$ENV_FILE introuvable (le frontend n'est pas encore installé ?)."
    read -rp "Chemin des photos sur le disque (Entrée = $PHOTO_STORAGE_PATH_DEFAULT) : " PHOTO_STORAGE_PATH_ANSWER
    PHOTO_STORAGE_PATH_ANSWER="${PHOTO_STORAGE_PATH_ANSWER:-$PHOTO_STORAGE_PATH_DEFAULT}"
    $SUDO mkdir -p "$ENV_DIR"
    printf 'PHOTO_STORAGE_PATH=%s\n' "$PHOTO_STORAGE_PATH_ANSWER" | $SUDO tee "$ENV_FILE" >/dev/null
    ok "Fichier $ENV_FILE créé (PHOTO_STORAGE_PATH=$PHOTO_STORAGE_PATH_ANSWER)."
fi
$SUDO chmod 644 "$ENV_FILE"

grep -q '^PHOTO_STORAGE_PATH=' "$ENV_FILE" || fail "PHOTO_STORAGE_PATH n'a pas pu être écrit dans $ENV_FILE."
PHOTO_STORAGE_PATH="$(grep -E '^PHOTO_STORAGE_PATH=' "$ENV_FILE" | tail -n1 | cut -d= -f2-)"
[ -n "$PHOTO_STORAGE_PATH" ] || fail "PHOTO_STORAGE_PATH vide dans $ENV_FILE."

if [ ! -d "$PHOTO_STORAGE_PATH" ]; then
    info "Le répertoire de stockage $PHOTO_STORAGE_PATH n'existe pas encore."
    confirm "Le créer maintenant ?" || fail "Créez-le à la main puis relancez le wizard (les photos doivent y vivre)."
    $SUDO mkdir -p "$PHOTO_STORAGE_PATH" || fail "Impossible de créer $PHOTO_STORAGE_PATH."
fi
ok "Répertoire de stockage : $PHOTO_STORAGE_PATH"

PHOTO_COUNT="$(find "$PHOTO_STORAGE_PATH" -type f | wc -l)"
ok "Photos présentes avant le premier déploiement CI : $PHOTO_COUNT (comptage de non-perte)."

# --- Étape 6 : merge check « Pipelines must succeed » -----------------------

info "Étape 6/8 : merge check « Pipelines must succeed »..."
echo
info "Le merge check empêche de merger une MR dont la pipeline est rouge (gouvernance de l'issue #15)."
read -rp "Collez un Personal Access Token GitLab avec le scope « api » pour que je l'active via l'API (vide = activation manuelle) : " GITLAB_PAT

if [ -n "$GITLAB_PAT" ]; then
    HTTP_CODE="$(curl -sS -o /tmp/gitlab-merge-check.json -w "%{http_code}" -X PUT \
        -H "PRIVATE-TOKEN: $GITLAB_PAT" \
        "https://gitlab.com/api/v4/projects/$PROJECT_API_PATH?only_allow_merge_if_pipeline_succeeds=true" \
        || echo 000)"
    if [ "$HTTP_CODE" = "200" ]; then
        ok "Merge check « Pipelines must succeed » activé via l'API."
    else
        warn "L'API a répondu HTTP $HTTP_CODE : $(head -c 300 /tmp/gitlab-merge-check.json 2>/dev/null || true)"
        warn "Vérifiez que le token a bien le scope « api » et appartient au propriétaire du projet."
        confirm "Continuer quand même (vous l'activerez dans l'UI) ?" || fail "Merge check non activé."
    fi
else
    echo
    echo "  Activez-le manuellement :"
    echo "  1. $PROJECT_WEB_URL/-/settings/merge_requests (Settings > Merge requests)"
    echo "  2. « Merge checks » : cochez « Pipelines must succeed »"
    echo "  3. « Save changes »"
    echo
    confirm "C'est fait ?" || warn "Le merge check reste désactivé — une MR rouge pourra être mergée tant qu'il ne l'est pas."
fi

# --- Étape 7 : test de bout en bout -----------------------------------------

info "Étape 7/8 : test de bout en bout (première pipeline master)..."
echo
info "Déclenchez maintenant la première pipeline backend :"
echo "  - soit depuis votre PC : git push gitlab master (repo GestionPhotosBackend)"
echo "  - soit dans GitLab : Build > Pipelines > Run pipeline (branche master)"
echo
read -rp "Appuyez sur Entrée quand la pipeline est lancée..."

info "Attente du premier déploiement CI (build + docker-build tournent sur les runners partagés ; le job deploy peut rester en attente quelques secondes le temps que le runner de la Pi le prenne)..."

DEPLOYED=""
for i in $(seq 1 120); do
    if $SUDO docker inspect "$CONTAINER_NAME" >/dev/null 2>&1; then
        IMAGE="$($SUDO docker inspect -f '{{.Config.Image}}' "$CONTAINER_NAME" 2>/dev/null || true)"
        case "$IMAGE" in
            registry.gitlab.com*) DEPLOYED=1; break ;;
        esac
    fi
    sleep 10
done

if [ -z "$DEPLOYED" ]; then
    warn "Le conteneur compose n'est pas encore apparu après 20 minutes."
    warn "Vérifiez la pipeline : $PROJECT_WEB_URL/-/pipelines  (le job deploy doit être vert)"
    fail "Test de bout en bout interrompu : relancez le wizard après correction."
fi
ok "Le conteneur compose tourne avec l'image du registry : $($SUDO docker inspect -f '{{.Config.Image}}' "$CONTAINER_NAME")"

# Fichier unique via mktemp : le wizard tourne en root et un chemin fixe
# laisserait derrière lui un fichier root:root qui fait échouer le job deploy
# CI (gitlab-runner ne peut pas l'écraser dans /tmp sticky — curl exit 23).
SMOKE_OK=""
SMOKE_FILE="$(mktemp /tmp/smoke-backend.XXXXXX)"
for i in $(seq 1 10); do
    if curl -fsS --max-time 10 "$SMOKE_URL" -o "$SMOKE_FILE" 2>/dev/null \
        && grep -q 'Service Photo est opérationnel' "$SMOKE_FILE"; then
        SMOKE_OK=1; break
    fi
    sleep 5
done
rm -f "$SMOKE_FILE"
if [ -z "$SMOKE_OK" ]; then
    fail "Le conteneur est lancé mais ne sert pas le message attendu sur $SMOKE_URL : docker logs $CONTAINER_NAME"
fi
ok "Smoke test OK : /api/photos/status sert « Service Photo est opérationnel »."

PHOTO_COUNT_AFTER="$(find "$PHOTO_STORAGE_PATH" -type f | wc -l)"
if [ "$PHOTO_COUNT_AFTER" -lt "$PHOTO_COUNT" ]; then
    fail "NON-PERTE DES PHOTOS : $PHOTO_COUNT fichier(s) avant le déploiement, $PHOTO_COUNT_AFTER après. Vérifiez immédiatement $PHOTO_STORAGE_PATH."
fi
ok "Non-perte des photos vérifiée : $PHOTO_COUNT fichier(s) avant, $PHOTO_COUNT_AFTER après."

# --- Étape 8 : retraite de l'ancien runner ----------------------------------

info "Étape 8/8 : retraite de l'ancien runner (dernière étape)..."
echo
info "Condition ADR 0001 remplie à cet instant : les DEUX chaînes CI/CD (frontend + backend) sont vertes sur le group runner."
echo
if ! confirm "Retirer maintenant l'ancien runner ?"; then
    echo
    warn "L'ancien runner reste en pause comme repli instantané du frontend."
    info "Procédure à exécuter plus tard (les deux gestes sont indépendants et tous deux requis) :"
    echo "  1. Suppression dans l'UI GitLab : $FRONTEND_WEB_URL/-/settings/ci_cd (Settings > CI/CD > Runners) > ancien runner > Delete runner"
    echo "  2. Unregister local : relancez ce wizard — il reprendra à cette étape et retirera"
    echo "     l'ancien par token (sans ambiguïté si plusieurs entrées portent le même nom)."
else
    echo
    info "Geste 1/2 : suppression dans l'UI GitLab..."
    echo "  1. Ouvrez : $FRONTEND_WEB_URL/-/settings/ci_cd (Settings > CI/CD > Runners)"
    echo "  2. « Project runners » : ancien runner (ex. gestion-photos-pi) > ... > Delete runner"
    confirm "Supprimé dans l'UI ?" || fail "Supprimez-le dans l'UI puis relancez le wizard (il reprendra à cette étape)."
    ok "Ancien runner supprimé de GitLab."

    info "Geste 2/2 : unregister local (par token)..."
    echo
    info "Retirez l'ancien runner, GARDEZ le nouveau (« $NEW_RUNNER_NAME »)."
    info "Le retrait se fait par token : plusieurs entrées peuvent porter le"
    info "même nom (vécu lors de la migration), le nom seul est ambigu."
    echo
    while :; do
        mapfile -t RUNNER_LIST < <(runner_entries)
        if [ "${#RUNNER_LIST[@]}" -eq 0 ]; then
            fail "Plus aucun runner dans $CONFIG_FILE — le nouveau a peut-être été retiré par erreur. Relancez le wizard : l'étape 2 le re-registrera."
        fi
        i=1
        for entry in "${RUNNER_LIST[@]}"; do
            entry_name="${entry%%|*}"
            entry_token="${entry#*|}"
            echo "  $i) ${entry_name:-« sans nom »}   (token ${entry_token:0:12}...)"
            i=$((i+1))
        done
        echo
        read -rp "Numéro du runner à retirer (Entrée = terminé) : " CHOICE
        if [ -z "$CHOICE" ]; then
            break
        fi
        case "$CHOICE" in
            *[!0-9]*) warn "Numéro invalide : entrez un numéro de la liste, ou Entrée pour terminer." ; continue ;;
        esac
        if [ "$CHOICE" -lt 1 ] || [ "$CHOICE" -gt "${#RUNNER_LIST[@]}" ]; then
            warn "Numéro $CHOICE hors liste."
            continue
        fi
        entry="${RUNNER_LIST[$((CHOICE-1))]}"
        entry_name="${entry%%|*}"
        entry_token="${entry#*|}"
        $SUDO gitlab-runner unregister --token "$entry_token" \
            || fail "Unregister échoué pour « $entry_name » (token ${entry_token:0:12}...)."
        ok "Runner « $entry_name » retiré de la configuration locale."
    done
    $SUDO systemctl restart gitlab-runner
    # Check final par validité, pas par nom (les noms peuvent se confondre) :
    # l'ancien runner, supprimé côté GitLab au geste 1/2, ne passe pas verify ;
    # le runner restant doit être « is valid ».
    $SUDO gitlab-runner verify >/dev/null 2>&1 \
        || fail "Le runner restant ne contacte pas GitLab (sudo gitlab-runner verify pour le détail). Le nouveau runner a peut-être été retiré à la place de l'ancien : relancez le wizard, l'étape 2 le re-registrera."
    ok "Ancien runner retiré ; le runner restant contacte GitLab (« is valid »)."
fi

echo
echo "============================================================"
echo "  Installation terminée : la chaîne CI/CD backend est vivante."
echo "============================================================"
echo
echo "  - Chaque push sur master déploie automatiquement sur la Pi (image tracée par le SHA du commit)."
echo "  - Le conteneur $CONTAINER_NAME a restart: unless-stopped : il revient tout seul après un reboot."
echo "  - Suivi des déploiements : $PROJECT_WEB_URL/-/environments (environnement « production »)"
echo "  - Rollback (~2 min, sans rebuild) : environnement « production » > dernier déploiement sain > Rollback"
echo "  - Déploiement manuel de secours (GitLab indisponible) : scripts/deploy.sh ou deploy.ps1"
