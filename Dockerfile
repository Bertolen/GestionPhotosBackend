# GestionPhotos Backend — image ARM64 pour la Raspberry Pi 3 B+ (prod).
#
# Multi-stage CI-first : le build Maven tourne en natif sur la plateforme du
# builder (x86 sur les runners SaaS — zéro code sous QEMU, le jar Spring Boot
# est du bytecode portable), seul le stage final est arm64.

# --- Stage 1 : build Maven (natif sur la plateforme du builder) ---
FROM --platform=$BUILDPLATFORM maven:3.9-eclipse-temurin-21 AS builder

WORKDIR /app

# Couches de dépendances seules d'abord (invalidées seulement si pom.xml change).
COPY pom.xml ./
RUN mvn -B -DskipTests dependency:go-offline

# Sources et package.
COPY src ./src
RUN mvn -B package -DskipTests

# --- Stage 2 : image finale JRE 21 (ARM64, Raspberry Pi 3 B+) ---
FROM eclipse-temurin:21-jre-jammy

WORKDIR /app

# Copier le JAR Spring Boot généré par le stage builder.
COPY --from=builder /app/target/*.jar app.jar

# Exposer le port de l'application (8080 par défaut pour Spring Boot).
EXPOSE 8080

# Commande de démarrage.
ENTRYPOINT ["java", "-jar", "app.jar"]
