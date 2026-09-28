# Dockerfile pour l'application GestionPhotos sur Raspberry Pi 4 (ARM64)
# Utilise une image JRE 21 légère pour ARM64

# Image de base : Eclipse Temurin JRE 21 pour ARM64
FROM eclipse-temurin:21-jre-jammy

# Répertoire de travail dans le conteneur
WORKDIR /app

# Copier le JAR généré par Maven
COPY target/*.jar app.jar

# Exposer le port de l'application (8080 par défaut pour Spring Boot)
EXPOSE 8080

# Commande de démarrage
ENTRYPOINT ["java", "-jar", "app.jar"]
