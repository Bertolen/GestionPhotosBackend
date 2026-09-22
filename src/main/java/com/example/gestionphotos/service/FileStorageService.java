package com.example.gestionphotos.service;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.UUID;

/**
 * Service pour gérer le stockage des fichiers photos sur le système de fichiers.
 * Les photos sont organisées par année/mois/jour dans la structure :
 * /storage/photos/{YYYY}/{MM}/{DD}/{filename}_{timestamp}.{ext}
 */
@Service
public class FileStorageService {

    private final Path rootLocation;
    
    // Formatter pour les dossiers : YYYY/MM/DD
    private static final DateTimeFormatter DATE_FOLDER_FORMATTER = DateTimeFormatter.ofPattern("yyyy/MM/dd");
    
    // Formatter pour les noms de fichiers : YYYY-MM-DD_HH-MM-SS
    private static final DateTimeFormatter FILE_NAME_FORMATTER = DateTimeFormatter.ofPattern("yyyy-MM-dd_HH-mm-ss");

    public FileStorageService(@Value("${app.storage.path}") String storagePath) throws IOException {
        this.rootLocation = Paths.get(storagePath).toAbsolutePath().normalize();
        Files.createDirectories(this.rootLocation);
    }

    /**
     * Sauvegarde un fichier multipart dans le système de stockage.
     * Crée l'arborescence de dossiers par date si nécessaire.
     * 
     * @param file le fichier à sauvegarder
     * @return le chemin relatif du fichier sauvegardé
     * @throws IOException en cas d'erreur de sauvegarde
     */
    public String store(MultipartFile file) throws IOException {
        // Générer un nom de fichier unique avec timestamp
        String originalFilename = StringUtils.cleanPath(file.getOriginalFilename());
        String extension = getFileExtension(originalFilename);
        String timestamp = LocalDateTime.now().format(FILE_NAME_FORMATTER);
        String uniqueId = UUID.randomUUID().toString().substring(0, 8);
        
        // Construire le nom du fichier : originalName_timestamp_UUID.ext
        String fileName = String.format("%s_%s_%s%s", 
                removeExtension(originalFilename), 
                timestamp, 
                uniqueId,
                extension);

        // Construire le chemin complet avec arborescence de dates
        String datePath = LocalDateTime.now().format(DATE_FOLDER_FORMATTER);
        Path destinationPath = this.rootLocation
                .resolve("photos")
                .resolve(datePath)
                .resolve(fileName)
                .normalize();

        // Créer les dossiers parents si nécessaire
        Files.createDirectories(destinationPath.getParent());

        // Sauvegarder le fichier
        Files.copy(file.getInputStream(), destinationPath, StandardCopyOption.REPLACE_EXISTING);

        // Retourner le chemin relatif (pour stocker en base ou dans le DTO)
        return Paths.get("photos").resolve(datePath).resolve(fileName).toString();
    }

    /**
     * Charge un fichier depuis le système de stockage.
     * 
     * @param storedPath le chemin relatif du fichier
     * @return le chemin absolu du fichier
     */
    public Path load(String storedPath) {
        return this.rootLocation.resolve(storedPath).normalize();
    }

    /**
     * Supprime un fichier du système de stockage.
     * 
     * @param storedPath le chemin relatif du fichier
     * @throws IOException en cas d'erreur de suppression
     */
    public void delete(String storedPath) throws IOException {
        Path filePath = this.rootLocation.resolve(storedPath).normalize();
        Files.deleteIfExists(filePath);
        
        // Optionnel : nettoyer les dossiers vides
        cleanupEmptyDirectories(filePath.getParent());
    }

    /**
     * Nettoie récursivement les dossiers vides à partir du chemin donné.
     * 
     * @param directory le dossier à vérifier
     */
    private void cleanupEmptyDirectories(Path directory) {
        try {
            if (directory != null && Files.isDirectory(directory)) {
                // Vérifier si le dossier est vide
                if (Files.list(directory).count() == 0) {
                    Files.delete(directory);
                    cleanupEmptyDirectories(directory.getParent());
                }
            }
        } catch (IOException e) {
            // Ignorer les erreurs de nettoyage (pas critique)
            System.err.println("Erreur lors du nettoyage des dossiers vides: " + e.getMessage());
        }
    }

    /**
     * Extrait l'extension d'un nom de fichier.
     * 
     * @param filename le nom du fichier
     * @return l'extension (incluant le point)
     */
    private String getFileExtension(String filename) {
        if (filename == null || filename.isEmpty()) {
            return ".unknown";
        }
        int lastDotIndex = filename.lastIndexOf('.');
        if (lastDotIndex < 0) {
            return ".unknown";
        }
        return filename.substring(lastDotIndex);
    }

    /**
     * Supprime l'extension d'un nom de fichier.
     * 
     * @param filename le nom du fichier
     * @return le nom sans extension
     */
    private String removeExtension(String filename) {
        if (filename == null || filename.isEmpty()) {
            return "unknown";
        }
        int lastDotIndex = filename.lastIndexOf('.');
        if (lastDotIndex < 0) {
            return filename;
        }
        return filename.substring(0, lastDotIndex);
    }

    /**
     * Vérifie si un fichier existe dans le stockage.
     * 
     * @param storedPath le chemin relatif du fichier
     * @return true si le fichier existe
     */
    public boolean exists(String storedPath) {
        return Files.exists(this.rootLocation.resolve(storedPath).normalize());
    }

    /**
     * Obtient la taille d'un fichier.
     * 
     * @param storedPath le chemin relatif du fichier
     * @return la taille en octets
     */
    public long getFileSize(String storedPath) throws IOException {
        return Files.size(this.rootLocation.resolve(storedPath).normalize());
    }

    /**
     * Obtient le chemin racine du stockage.
     * 
     * @return le chemin racine
     */
    public Path getRootLocation() {
        return rootLocation;
    }
}
