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
 * Service metier pour la gestion des photos.
 * Gere l'upload, la recuperation, la suppression et la liste des photos.
 */
@Service
public class PhotoService {

    private final FileStorageService fileStorageService;
    
    // Taille maximale des fichiers (en octets) - configurable
    @Value("${app.upload.max-file-size:10485760}") // 10 Mo par defaut
    private long maxFileSize;
    
    // Types MIME autorises
    @Value("${app.upload.allowed-mime-types:image/jpeg,image/png,image/gif,image/webp}")
    private String[] allowedMimeTypes;

    @Autowired
    public PhotoService(FileStorageService fileStorageService) {
        this.fileStorageService = fileStorageService;
    }

    /**
     * Upload une photo et retourne son DTO avec les metadonnees.
     * 
     * @param file le fichier a uploader
     * @return PhotoDto contenant les metadonnees de la photo sauvegardee
     * @throws IOException en cas d'erreur de sauvegarde
     * @throws IllegalArgumentException si le fichier est invalide
     */
    public PhotoDto uploadPhoto(MultipartFile file) throws IOException {
        validateFile(file);
        
        String storedPath = fileStorageService.store(file);
        // Extraire la date de creation depuis le nom de fichier (format: YYYYMMDD_HHmmss_UUID8.ext)
        String fileName = Path.of(storedPath).getFileName().toString();
        LocalDateTime creationDate = PhotoTimestampUtils.extractPhotoTimestamp(fileName)
                .orElse(LocalDateTime.now());
        
        return doUploadPhoto(file, storedPath, creationDate);
    }

    /**
     * Upload plusieurs photos.
     * 
     * @param files les fichiers a uploader
     * @return Liste de PhotoDto pour chaque photo sauvegardee
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
     * Upload une photo avec une date de creation specifiee.
     * 
     * @param file le fichier a uploader
     * @param creationDate la date de creation a utiliser pour la photo
     * @return PhotoDto contenant les metadonnees de la photo sauvegardee
     * @throws IOException en cas d'erreur de sauvegarde
     * @throws IllegalArgumentException si le fichier est invalide
     */
    public PhotoDto uploadPhotoWithDate(MultipartFile file, LocalDateTime creationDate) throws IOException {
        validateFile(file);
        
        String storedPath = fileStorageService.store(file);
        
        return doUploadPhoto(file, storedPath, creationDate);
    }

    /**
     * Methode interne commune pour creer un PhotoDto apres validation et sauvegarde.
     * 
     * @param file le fichier deja valide
     * @param storedPath le chemin ou le fichier a ete sauvegarde
     * @param creationDate la date de creation a utiliser
     * @return PhotoDto contenant les metadonnees de la photo
     */
    private PhotoDto doUploadPhoto(MultipartFile file, String storedPath, LocalDateTime creationDate) {
        String fileName = Path.of(storedPath).getFileName().toString();
        String uuid = extractUuidFromFilename(fileName);
        LocalDateTime now = LocalDateTime.now();
        
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
     * Upload plusieurs photos avec leurs dates de creation specifiees.
     * 
     * @param files les fichiers a uploader
     * @param creationDates liste des dates de creation pour chaque fichier (doit avoir la meme taille que files)
     * @return Liste de PhotoDto pour chaque photo sauvegardee
     * @throws IOException en cas d'erreur de sauvegarde
     * @throws IllegalArgumentException si un fichier est invalide ou si les tailles ne correspondent pas
     */
    public List<PhotoDto> uploadPhotosWithDates(MultipartFile[] files, List<LocalDateTime> creationDates) throws IOException {
        if (files == null || creationDates == null || files.length != creationDates.size()) {
            throw new IllegalArgumentException("Le nombre de fichiers doit correspondre au nombre de dates de creation");
        }
        
        List<PhotoDto> uploadedPhotos = new ArrayList<>();
        List<Exception> errors = new ArrayList<>();
        
        for (int i = 0; i < files.length; i++) {
            try {
                if (!files[i].isEmpty()) {
                    uploadedPhotos.add(uploadPhotoWithDate(files[i], creationDates.get(i)));
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
     * Recupere la liste de toutes les photos disponibles.
     * Parcourt le repertoire de stockage pour trouver tous les fichiers.
     * 
     * @return Liste de PhotoDto pour toutes les photos
     */
    public List<PhotoDto> getAllPhotos() {
        List<PhotoDto> photos = new ArrayList<>();
        
        try {
            Path rootPath = fileStorageService.getRootLocation();
            
            if (Files.exists(rootPath)) {
                // Parcourir recursivement tous les fichiers dans le repertoire racine
                try (Stream<Path> paths = Files.walk(rootPath)) {
                    photos = paths
                            .filter(Files::isRegularFile)
                            .filter(path -> !path.equals(rootPath)) // Exclure le dossier racine lui-meme
                            .map(this::convertPathToPhotoDto)
                            .filter(photo -> photo != null)
                            .sorted((p1, p2) -> p2.creationDate().compareTo(p1.creationDate())) // Tri par date decroissante
                            .collect(Collectors.toList());
                }
            }
        } catch (IOException e) {
            System.err.println("Erreur lors de la recuperation des photos: " + e.getMessage());
        }
        
        return photos;
    }

    /**
     * Recupere une photo par son ID.
     * 
     * @param photoId l'ID de la photo
     * @return PhotoDto de la photo correspondante, ou null si non trouvee
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
     * @param photoId l'ID de la photo a supprimer
     * @return true si la suppression a reussi, false sinon
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
     * Extrait l'UUID de 8 caracteres du nom de fichier.
     * Format attendu : YYYYMMDD_HHmmss_[UUID8].ext
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
        
        // Extraire la partie apRES le dernier underscore
        String uuidPart = withoutExt.substring(lastUnderscore + 1);
        
        // Verifier que c'est bien un UUID de 8 caracteres hexadecimaux
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
     * @param file le fichier a valider
     * @throws IllegalArgumentException si le fichier est invalide
     */
    private void validateFile(MultipartFile file) {
        if (file == null || file.isEmpty()) {
            throw new IllegalArgumentException("Le fichier ne peut pas etre vide");
        }
        
        if (file.getSize() > maxFileSize) {
            throw new IllegalArgumentException(
                    String.format("La taille du fichier (%d octets) depasse la limite autorisee (%d octets)", 
                            file.getSize(), maxFileSize));
        }
        
        if (!isAllowedMimeType(file.getContentType())) {
            throw new IllegalArgumentException(
                    String.format("Le type MIME '%s' n'est pas autorise", file.getContentType()));
        }
    }

    /**
     * Verifie si un type MIME est autorise.
     * 
     * @param mimeType le type MIME a verifier
     * @return true si le type est autorise
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
     * Recupere les photos filtrees par date.
     * 
     * @param fromDate date de debut (inclusive)
     * @param toDate date de fin (inclusive)
     * @return Liste de PhotoDto filtrees
     */
    public List<PhotoDto> getPhotosByDateRange(LocalDateTime fromDate, LocalDateTime toDate) {
        return getAllPhotos().stream()
                .filter(photo -> !photo.creationDate().isBefore(fromDate))
                .filter(photo -> !photo.creationDate().isAfter(toDate))
                .sorted((p1, p2) -> p1.creationDate().compareTo(p2.creationDate()))
                .collect(Collectors.toList());
    }

    /**
     * Recupere les photos triees par date de creation (decroissante).
     * 
     * @return Liste de PhotoDto triees
     */
    public List<PhotoDto> getPhotosSortedByDate() {
        List<PhotoDto> photos = getAllPhotos();
        photos.sort((p1, p2) -> p2.creationDate().compareTo(p1.creationDate()));
        return photos;
    }

    /**
     * Recupere plusieurs photos par leurs identifiants.
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
