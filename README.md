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
app.storage.path=./photos-storage

# Taille maximale des uploads (10 Mo)
app.upload.max-file-size=10485760

# Types MIME autorisés
app.upload.allowed-mime-types=image/jpeg,image/png,image/gif,image/webp,image/jpg
```

### Pour le développement sur Windows
Le chemin `./photos-storage` créera un dossier dans le répertoire du projet.

### Pour la production sur Raspberry Pi
Modifiez dans `application.properties` :
```properties
app.storage.path=/home/pi/photo-storage
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

## 📝 Notes

- Les photos sont stockées avec un nom unique incluant un timestamp et un UUID
- L'arborescence est automatique : `YYYY/MM/DD/filename_timestamp_UUID.ext`
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
