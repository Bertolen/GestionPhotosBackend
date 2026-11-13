# Gestion Photos - Backend Spring Boot

Application Spring Boot 4.0 pour la gestion de photos avec Java 21.

## 📋 Description

Ce projet est le backend d'une application de gestion de photos qui permet :
- **Upload** de photos (simple ou multiple)
- **Visualisation** des photos triées par date et heure
- **Téléchargement** de photos individuelles
- **Suppression** de photos
- **Filtrage** par date

Les photos sont automatiquement organisées dans une arborescence : `/storage/photos/YYYY/MM/DD/`

## 🏗️ Architecture

```
Backend (Spring Boot 4.0) → Port 8080
├── PhotoController      - Endpoints REST API
├── PhotoService         - Logique métier
├── FileStorageService   - Stockage des fichiers
├── StoredPhoto          - Métadonnées d'une photo sauvegardée (couture PhotoService/FileStorageService)
└── PhotoDto             - DTO pour les métadonnées

Frontend (Angular 25) → Port 4200
├── PhotoGalleryComponent - Affichage des photos
├── PhotoUploadComponent  - Upload de fichiers
└── PhotoService          - Communication avec l'API
```

## 🚀 Démarrage rapide

### Prérequis
- Java 21+ (OpenJDK ou Oracle JDK)
- Maven 3.9+
- Node.js 18+ (pour le frontend Angular)

### Lancement du Backend

#### Avec IDE (IntelliJ, Eclipse, VS Code)
1. Ouvrir le projet dans votre IDE
2. Exécuter la classe `GestionPhotosApplication`
3. Le serveur démarrera sur http://localhost:8080

#### Avec Maven en ligne de commande
```bash
mvn spring-boot:run
```

### Lancement du Frontend (Angular)
```bash
cd ../GestionPhotosFrontend
npm install
npm start
```
Le frontend sera accessible sur http://localhost:4200

## 🔌 Endpoints API

| Méthode | Endpoint | Description |
|---------|----------|-------------|
| GET | `/api/photos` | Liste toutes les photos (triées par date décroissante) |
| GET | `/api/photos/{id}` | Récupère une photo par ID |
| GET | `/api/photos/{id}/download` | Télécharge une photo |
| GET | `/api/photos/{id}/metadata` | Récupère les métadonnées d'une photo |
| GET | `/api/photos/by-date?fromDate=...&toDate=...` | Filtre les photos par date |
| POST | `/api/photos/upload` | Upload une photo (form-data: file) |
| POST | `/api/photos/upload/multiple` | Upload plusieurs photos (form-data: files) |
| DELETE | `/api/photos/{id}` | Supprime une photo |
| GET | `/api/photos/status` | Vérifie que le service est opérationnel |

## 📂 Structure des fichiers

```
GestionArgent/
├── src/
│   └── main/
│       ├── java/
│       │   └── com/
│       │       └── example/
│       │           ├── gestionphotos/          # Nouvelle application
│       │           │   ├── config/             # Configurations
│       │           │   ├── controller/         # Controllers REST
│       │           │   ├── dto/               # Data Transfer Objects
│       │           │   └── service/           # Services métier
│       │           │       └── GestionPhotosApplication.java
│       └── resources/
│           └── application.properties        # Configuration
├── pom.xml                              # Dépendances Maven
└── .vscode/
    └── launch.json                      # Configuration VS Code
```

## ⚙️ Configuration

### application.properties

```properties
# Port du serveur
server.port=8080

# Chemin de stockage des photos
app.storage.path=${APP_STORAGE_PATH:./photos-storage}

# Taille maximale des uploads (1 Go)
app.upload.max-file-size=1073741824

# Types MIME autorisés
app.upload.allowed-mime-types=image/jpeg,image/png,image/gif,image/webp,image/jpg
```

### Pour le développement sur Windows
Le chemin `./photos-storage` créera un dossier dans le répertoire du projet.

### Pour la production sur Raspberry Pi

La prod est une **Raspberry Pi 3 B+** (ARM64), hostname `nuestra-traviesa` (résolu par le DNS de la box) : le backend Spring Boot occupe le port **8080** (le frontend nginx occupe le 80).

- L'image est publiée dans le **Container Registry GitLab** par la CI (tags = SHA court du commit + `latest`).
- Le conteneur `gestion-photos-backend` (docker compose, `restart: unless-stopped`) revient tout seul après un reboot de la Pi.
- `APP_STORAGE_PATH=/data/photos` est **fixé dans `docker-compose.yml`** : le conteneur sert toujours les photos depuis `/data/photos`.
- Le chemin hôte est une variable d'exécution : `PHOTO_STORAGE_PATH` dans le `.env` de la Pi (défaut `/mnt/disque/photo-storage`, sur le disque externe monté sur `/mnt/disque`). Changer le chemin hôte ne nécessite ni rebuild ni variable de CI.

```bash
# Sur la Pi (le .env est créé par scripts/setup-pi-runner.sh) :
docker compose --env-file /home/pi/gestion-photos/.env up -d
```

## 📦 Dépendances

- **Spring Boot 4.0.0**
- **Spring Web** - Pour les controllers REST
- **Java 21** - Version LTS

## 🔧 Build

### Build avec Maven
```bash
mvn clean package
```

Le fichier JAR sera généré dans `target/gestion-photos-0.0.1-SNAPSHOT.jar`

### Exécuter le JAR
```bash
java -jar target/gestion-photos-0.0.1-SNAPSHOT.jar
```

## 🧪 Tests

Les endpoints peuvent être testés avec :
- **cURL**
- **Postman**
- **Swagger** (à ajouter)
- **Frontend Angular**

### Exemple avec cURL

```bash
# Upload une photo
curl -X POST -F "file=@/path/to/photo.jpg" http://localhost:8080/api/photos/upload

# Lister les photos
curl http://localhost:8080/api/photos

# Télécharger une photo
curl http://localhost:8080/api/photos/{id}/download --output photo.jpg
```

## 🚀 CI/CD & déploiement

La chaîne est définie dans `.gitlab-ci.yml` (spec : issue #15), parité de la chaîne frontend :

- **Merge request** : job `build` (`mvn -B verify` — compilation, 103 tests, package, cache `.m2`) et job `docker-build` (image Docker **ARM64** via buildx/QEMU, validation sans publication). Une MR dont la pipeline est rouge ne peut pas être mergée (merge check « Pipelines must succeed »).
- **Push ou merge sur master** : les mêmes jobs + publication de l'image dans le Container Registry GitLab (jeton CI automatique, aucun secret manuel), puis **déploiement automatique sur la Pi** : garde disque externe → pull → remplacement de l'éventuel conteneur manuel → `docker compose up -d` → smoke test sur `GET /api/photos/status`.
- Les builds tournent sur les **runners partagés GitLab.com** ; la Pi n'exécute que le job `deploy`, via un **group runner** `nuestra-traviesa` taggé `rpi` (ADR 0001), limité à 1 job à la fois.
- **Rollback** (~2 min, sans rebuild) : Build > Environments > `production` > déploiement précédent > Rollback.

Les pipelines ne se déclenchent que sur les MR et sur master ; la branche master n'est pas protégée (push directs autorisés, déployés s'ils passent build + tests).

### Installation sur la Pi (une seule fois)

Le wizard `scripts/setup-pi-runner.sh` est auto-vérifiant : prérequis (aarch64, Docker, plugin compose, disque externe), migration vers le group runner (ADR 0001), fstab du disque des photos (`nofail,user`), `.env` partagé (`PHOTO_STORAGE_PATH`), merge check, test de bout en bout, retraite de l'ancien runner.

```bash
ssh bertolen@nuestra-traviesa
git clone https://gitlab.com/nuestra-traviesa/GestionPhotosBackend.git
cd GestionPhotosBackend
sudo bash scripts/setup-pi-runner.sh
```

### Déploiement manuel de secours (GitLab indisponible)

Les scripts `scripts/deploy.sh` (bash) et `scripts/deploy.ps1` (PowerShell) construisent l'image ARM64, la transfèrent par scp et la démarrent sur la Pi avec `docker run` (même nom de conteneur que compose : le prochain déploiement CI le remplacera automatiquement) :

```bash
cp .env.example .env   # valeurs réelles déjà remplies
./scripts/deploy.sh    # ou : powershell -ExecutionPolicy Bypass -File scripts/deploy.ps1
```

## 📝 Notes

- Les photos sont stockées avec un nom unique incluant un timestamp et un UUID
- L'arborescence est automatique : `YYYY/MM/DD/filename_timestamp_UUID.ext`. Pour les photos
  nommées selon la convention Android `YYYYMMDD_HHmmss.ext` (par exemple
  `20260920_191817.jpg`), la date des dossiers est extraite du nom ; sinon, la date
  d'upload est utilisée.
- Les types de fichiers acceptés : JPEG, PNG, GIF, WEBP
- Taille maximale par défaut : 10 Mo (configurable)

## 📞 Support

Pour toute question ou problème, vérifiez :
1. Que Java 21 est installé
2. Que Maven est configuré
3. Que le port 8080 est disponible
4. Les logs de l'application pour les erreurs

## 📄 Licence

Projet personnel - Tous droits réservés
