package com.example.gestionphotos.service;

import com.example.gestionphotos.exception.DuplicatePhotoException;
import com.example.gestionphotos.utils.PhotoTimestampUtils;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;
import org.springframework.web.multipart.MultipartFile;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.regex.Pattern;
import java.util.UUID;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

/**
 * Service pour gerer le stockage des fichiers photos sur le systeme de fichiers.
 * Les fichiers sont nommes avec leur timestamp de creation : YYYYMMDD_HHmmss_[UUID8].ext
 */
@Service
public class FileStorageService {

    private final Path rootLocation;

    public FileStorageService(@Value("${app.storage.path}") String storagePath) throws IOException {
        this.rootLocation = Paths.get(storagePath).toAbsolutePath().normalize();
        Files.createDirectories(this.rootLocation);
    }

    /**
     * Sauvegarde un fichier multipart dans le systeme de stockage.
     * Les fichiers sont nommes avec leur timestamp de creation : YYYYMMDD_HHmmss_[UUID8].ext
     * 
     * @param file le fichier a sauvegarder
     * @return le chemin relatif du fichier sauvegarde
     * @throws IOException en cas d'erreur de sauvegarde
     */
    public String store(MultipartFile file) throws IOException {
        String originalFilename = StringUtils.cleanPath(file.getOriginalFilename());
        String extension = getFileExtension(originalFilename);
        LocalDateTime uploadDate = LocalDateTime.now();
        
        // Extraire le timestamp depuis le nom de fichier (si disponible) ou utiliser la date actuelle
        LocalDateTime creationDateTime = PhotoTimestampUtils.extractPhotoTimestamp(originalFilename)
                .orElse(uploadDate);
        
        // Formater le timestamp au format YYYYMMDD_HHmmss
        String timestamp = creationDateTime.format(DateTimeFormatter.ofPattern("yyyyMMdd_HHmmss"));
        
        // Verifier si une photo avec ce timestamp existe deja
        if (isAlreadyStoredByTimestamp(timestamp, extension)) {
            throw new DuplicatePhotoException(
                    "Une photo avec le timestamp " + timestamp + " existe deja dans le systeme de stockage");
        }
        
        // Generer un UUID unique
        String uniqueId = UUID.randomUUID().toString().substring(0, 8);
        
        // Construire le nom du fichier : YYYYMMDD_HHmmss_[UUID8].ext
        String fileName = String.format("%s_%s%s", timestamp, uniqueId, extension);

        // Construire le chemin complet dans le repertoire racine
        Path destinationPath = this.rootLocation
                .resolve(fileName)
                .normalize();

        // Creer le dossier s'il n'existe pas
        Files.createDirectories(this.rootLocation);

        // Sauvegarder le fichier
        Files.copy(file.getInputStream(), destinationPath, StandardCopyOption.REPLACE_EXISTING);

        // Retourner le chemin relatif
        return fileName.replace('\\', '/');
    }

    /**
     * Verifie si une photo avec ce timestamp existe deja dans le stockage.
     * 
     * @param timestamp le timestamp au format YYYYMMDD_HHmmss
     * @param extension l'extension du fichier
     * @return true si une photo avec ce timestamp existe deja
     * @throws IOException en cas d'erreur de parcours du stockage
     */
    private boolean isAlreadyStoredByTimestamp(String timestamp, String extension) throws IOException {
        if (timestamp == null || timestamp.isBlank() || extension == null || extension.isBlank()) {
            return false;
        }

        // Pattern pour matcher : timestamp + _ + UUID8 + extension
        String pattern = Pattern.quote(timestamp) + "_[a-f0-9]{8}" + Pattern.quote(extension) + "$";
        
        try (var paths = Files.walk(this.rootLocation)) {
            return paths
                    .filter(Files::isRegularFile)
                    .map(path -> path.getFileName().toString())
                    .anyMatch(filename -> filename.matches(pattern));
        }
    }

    /**
     * Charge un fichier depuis le systeme de stockage.
     * 
     * @param storedPath le chemin relatif du fichier
     * @return le chemin absolu du fichier
     */
    public Path load(String storedPath) {
        return this.rootLocation.resolve(storedPath).normalize();
    }

    /**
     * Supprime un fichier du systeme de stockage.
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
     * Nettoie recursivement les dossiers vides a partir du chemin donne.
     * 
     * @param directory le dossier a verifier
     */
    private void cleanupEmptyDirectories(Path directory) {
        try {
            if (directory != null && Files.isDirectory(directory)) {
                // Verifier si le dossier est vide
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
    String getFileExtension(String filename) {
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
    String removeExtension(String filename) {
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
     * Verifie si un fichier existe dans le stockage.
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

    /**
     * Cree un fichier ZIP contenant plusieurs fichiers.
     * 
     * @param storedPaths liste des chemins relatifs des fichiers a inclure
     * @return tableau d'octets contenant le fichier ZIP
     * @throws IOException en cas d'erreur de creation du ZIP
     */
    public byte[] createZipFromPaths(List<String> storedPaths) throws IOException {
        ByteArrayOutputStream byteArrayOutputStream = new ByteArrayOutputStream();
        
        try (ZipOutputStream zipOutputStream = new ZipOutputStream(byteArrayOutputStream)) {
            for (String storedPath : storedPaths) {
                Path filePath = this.rootLocation.resolve(storedPath).normalize();
                if (Files.exists(filePath) && Files.isRegularFile(filePath)) {
                    // Utiliser seulement le nom de fichier dans le ZIP pour eviter les chemins absolus
                    String fileName = Path.of(storedPath).getFileName().toString();
                    ZipEntry zipEntry = new ZipEntry(fileName);
                    zipOutputStream.putNextEntry(zipEntry);
                    Files.copy(filePath, zipOutputStream);
                    zipOutputStream.closeEntry();
                }
            }
        }
        
        return byteArrayOutputStream.toByteArray();
    }
}
