package com.example.gestionphotos.service;

import com.example.gestionphotos.dto.PhotoDto;
import com.example.gestionphotos.exception.MultiplePhotoUploadException;
import com.example.gestionphotos.utils.PhotoTimestampUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;
import java.util.stream.Stream;

/**
 * Service métier pour la gestion des photos.
 * Gère l'upload, la récupération, la suppression et la liste des photos.
 */
@Service
public class PhotoService {

    private final FileStorageService fileStorageService;
    
    // Taille maximale des fichiers (en octets) - configurable
    @Value("${app.upload.max-file-size:10485760}") // 10 Mo par défaut
    private long maxFileSize;
    
    // Types MIME autorisés
    @Value("${app.upload.allowed-mime-types:image/jpeg,image/png,image/gif,image/webp}")
    private String[] allowedMimeTypes;

    @Autowired
    public PhotoService(FileStorageService fileStorageService) {
        this.fileStorageService = fileStorageService;
    }

    /**
     * Upload une photo et retourne son DTO avec les métadonnées.
     * 
     * @param file le fichier à uploader
     * @return PhotoDto contenant les métadonnées de la photo sauvegardée
     * @throws IOException en cas d'erreur de sauvegarde
     * @throws IllegalArgumentException si le fichier est invalide
     */
    public PhotoDto uploadPhoto(MultipartFile file) throws IOException {
        // Validation du fichier
        validateFile(file);
        
        // Sauvegarder le fichier
        String storedPath = fileStorageService.store(file);
        String fileName = Path.of(storedPath).getFileName().toString();
        String uuid = extractUuidFromFilename(fileName);
        LocalDateTime now = LocalDateTime.now();
        LocalDateTime creationDate = now;
        
        // Récupérer la date de création du fichier (si disponible)
        try {
            Path filePath = fileStorageService.load(storedPath);
            creationDate = Files.getLastModifiedTime(filePath)
                    .toInstant()
                    .atZone(java.time.ZoneId.systemDefault())
                    .toLocalDateTime();
        } catch (Exception e) {
            // Si on ne peut pas récupérer la date de création, utiliser la date d'upload
        }
        
        return new PhotoDto(
            uuid,
            file.getOriginalFilename(),
            storedPath,
            fileName,
            file.getSize(),
            file.getContentType(),
            now,
            creationDate
        );
    }

    /**
     * Upload plusieurs photos.
     * 
     * @param files les fichiers à uploader
     * @return Liste de PhotoDto pour chaque photo sauvegardée
     * @throws IOException en cas d'erreur de sauvegarde
     * @throws IllegalArgumentException si un fichier est invalide
     */
    public List<PhotoDto> uploadPhotos(MultipartFile[] files) throws IOException {
        List<PhotoDto> uploadedPhotos = new ArrayList<>();
        List<Exception> errors = new ArrayList<>();
        
        for (MultipartFile file : files) {
            try {
                if (!file.isEmpty()) {
                    uploadedPhotos.add(uploadPhoto(file));
                }
            } catch (Exception e) {
                errors.add(e);
            }
        }

        if (!errors.isEmpty()) {
            throw new MultiplePhotoUploadException(uploadedPhotos, errors);
        }
        
        return uploadedPhotos;
    }

    /**
     * Récupère la liste de toutes les photos disponibles.
     * Parcourt le répertoire de stockage pour trouver tous les fichiers.
     * 
     * @return Liste de PhotoDto pour toutes les photos
     */
    public List<PhotoDto> getAllPhotos() {
        List<PhotoDto> photos = new ArrayList<>();
        
        try {
            Path photosDir = fileStorageService.getRootLocation().resolve("photos");
            
            if (Files.exists(photosDir)) {
                // Parcourir récursivement tous les fichiers dans le dossier photos
                try (Stream<Path> paths = Files.walk(photosDir)) {
                    photos = paths
                            .filter(Files::isRegularFile)
                            .map(this::convertPathToPhotoDto)
                            .sorted((p1, p2) -> p2.creationDate().compareTo(p1.creationDate())) // Tri par date décroissante
                            .collect(Collectors.toList());
                }
            }
        } catch (IOException e) {
            System.err.println("Erreur lors de la récupération des photos: " + e.getMessage());
        }
        
        return photos;
    }

    /**
     * Récupère une photo par son ID.
     * 
     * @param photoId l'ID de la photo
     * @return PhotoDto de la photo correspondante, ou null si non trouvée
     */
    public PhotoDto getPhotoById(String photoId) {
        return getAllPhotos().stream()
                .filter(photo -> photo.id().equals(photoId))
                .findFirst()
                .orElse(null);
    }

    /**
     * Supprime une photo par son ID.
     * 
     * @param photoId l'ID de la photo à supprimer
     * @return true si la suppression a réussi, false sinon
     */
    public boolean deletePhoto(String photoId) {
        PhotoDto photo = getPhotoById(photoId);
        
        if (photo != null) {
            try {
                fileStorageService.delete(photo.storedPath());
                return true;
            } catch (IOException e) {
                System.err.println("Erreur lors de la suppression de la photo: " + e.getMessage());
                return false;
            }
        }
        
        return false;
    }

    /**
     * Extrait l'UUID de 8 caractères du nom de fichier.
     * Format attendu : {nom}_yyyy-MM-dd_HH-mm-ss_{UUID8}.{ext}
     * 
     * @param filename le nom du fichier
     * @return l'UUID extrait, ou le nom complet en fallback
     */
    private String extractUuidFromFilename(String filename) {
        // Enlever l'extension
        int lastDot = filename.lastIndexOf('.');
        String withoutExt = lastDot > 0 ? filename.substring(0, lastDot) : filename;
        
        // Trouver le dernier underscore
        int lastUnderscore = withoutExt.lastIndexOf('_');
        if (lastUnderscore < 0 || lastUnderscore >= withoutExt.length() - 1) {
            return filename;
        }
        
        // Extraire la partie après le dernier underscore
        String uuidPart = withoutExt.substring(lastUnderscore + 1);
        
        // Vérifier que c'est bien un UUID de 8 caractères hexadécimaux
        if (uuidPart.length() == 8 && uuidPart.matches("[a-f0-9]{8}")) {
            return uuidPart;
        }
        
        return filename;
    }

    /**
     * Convertit un chemin de fichier en PhotoDto.
     * 
     * @param path le chemin du fichier
     * @return PhotoDto correspondant
     */
    private PhotoDto convertPathToPhotoDto(Path path) {
        try {
            Path rootPath = fileStorageService.getRootLocation();
            Path relativePath = rootPath.relativize(path);
            String fileName = path.getFileName().toString();
            String uuid = extractUuidFromFilename(fileName);
            
            // Date de modification = date d'upload approximative
            LocalDateTime modifiedDate = Files.getLastModifiedTime(path)
                    .toInstant()
                    .atZone(java.time.ZoneId.systemDefault())
                    .toLocalDateTime();
            LocalDateTime creationDate = PhotoTimestampUtils.extractPhotoTimestamp(fileName)
                    .orElse(modifiedDate);
            
            return new PhotoDto(
                uuid,
                fileName,
                relativePath.toString(),
                fileName,
                Files.size(path),
                Files.probeContentType(path),
                modifiedDate,
                creationDate
            );
        } catch (IOException e) {
            System.err.println("Erreur lors de la conversion du chemin en PhotoDto: " + e.getMessage());
            return null;
        }
    }

    /**
     * Valide un fichier avant upload.
     * 
     * @param file le fichier à valider
     * @throws IllegalArgumentException si le fichier est invalide
     */
    private void validateFile(MultipartFile file) {
        if (file == null || file.isEmpty()) {
            throw new IllegalArgumentException("Le fichier ne peut pas être vide");
        }
        
        if (file.getSize() > maxFileSize) {
            throw new IllegalArgumentException(
                    String.format("La taille du fichier (%d octets) dépasse la limite autorisée (%d octets)", 
                            file.getSize(), maxFileSize));
        }
        
        if (!isAllowedMimeType(file.getContentType())) {
            throw new IllegalArgumentException(
                    String.format("Le type MIME '%s' n'est pas autorisé", file.getContentType()));
        }
    }

    /**
     * Vérifie si un type MIME est autorisé.
     * 
     * @param mimeType le type MIME à vérifier
     * @return true si le type est autorisé
     */
    private boolean isAllowedMimeType(String mimeType) {
        if (mimeType == null) {
            return false;
        }
        
        for (String allowedType : allowedMimeTypes) {
            if (mimeType.equalsIgnoreCase(allowedType)) {
                return true;
            }
        }
        
        return false;
    }

    /**
     * Récupère les photos filtrées par date.
     * 
     * @param fromDate date de début (inclusive)
     * @param toDate date de fin (inclusive)
     * @return Liste de PhotoDto filtrées
     */
    public List<PhotoDto> getPhotosByDateRange(LocalDateTime fromDate, LocalDateTime toDate) {
        return getAllPhotos().stream()
                .filter(photo -> !photo.creationDate().isBefore(fromDate))
                .filter(photo -> !photo.creationDate().isAfter(toDate))
                .sorted((p1, p2) -> p1.creationDate().compareTo(p2.creationDate()))
                .collect(Collectors.toList());
    }

    /**
     * Récupère les photos triées par date de création (décroissante).
     * 
     * @return Liste de PhotoDto triées
     */
    public List<PhotoDto> getPhotosSortedByDate() {
        List<PhotoDto> photos = getAllPhotos();
        photos.sort((p1, p2) -> p2.creationDate().compareTo(p1.creationDate()));
        return photos;
    }

    /**
     * Récupère plusieurs photos par leurs identifiants.
     * 
     * @param photoIds liste des identifiants de photos
     * @return Liste de PhotoDto correspondantes
     */
    public List<PhotoDto> getPhotosByIds(List<String> photoIds) {
        if (photoIds == null || photoIds.isEmpty()) {
            return new ArrayList<>();
        }
        
        return getAllPhotos().stream()
                .filter(photo -> photoIds.contains(photo.id()))
                .collect(Collectors.toList());
    }
}
