package com.example.gestionphotos.controller;

import com.example.gestionphotos.dto.PhotoDto;
import com.example.gestionphotos.service.FileStorageService;
import com.example.gestionphotos.service.PhotoService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.core.io.Resource;
import org.springframework.core.io.UrlResource;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.nio.file.Path;
import java.time.LocalDateTime;
import java.util.List;

/**
 * Controller REST pour la gestion des photos.
 * Expose les endpoints pour l'upload, le téléchargement, la liste et la suppression des photos.
 */
@RestController
@RequestMapping("/api/photos")
public class PhotoController {

    private final PhotoService photoService;
    private final FileStorageService fileStorageService;

    @Autowired
    public PhotoController(PhotoService photoService, FileStorageService fileStorageService) {
        this.photoService = photoService;
        this.fileStorageService = fileStorageService;
    }

    /**
     * Upload une photo.
     * 
     * @param file le fichier à uploader
     * @return ResponseEntity avec le PhotoDto de la photo sauvegardée
     */
    @PostMapping("/upload")
    public ResponseEntity<PhotoDto> uploadPhoto(@RequestParam("file") MultipartFile file) {
        try {
            PhotoDto photoDto = photoService.uploadPhoto(file);
            return ResponseEntity.ok(photoDto);
        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest().body(null); // Le frontend gérera l'erreur
        } catch (IOException e) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).build();
        }
    }

    /**
     * Upload plusieurs photos.
     * 
     * @param files les fichiers à uploader
     * @return ResponseEntity avec la liste des PhotoDto sauvegardées
     */
    @PostMapping("/upload/multiple")
    public ResponseEntity<List<PhotoDto>> uploadPhotos(@RequestParam("files") MultipartFile[] files) {
        try {
            List<PhotoDto> photoDtos = photoService.uploadPhotos(files);
            return ResponseEntity.ok(photoDtos);
        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest().body(null);
        } catch (IOException e) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).build();
        }
    }

    /**
     * Récupère la liste de toutes les photos.
     * 
     * @return ResponseEntity avec la liste des PhotoDto
     */
    @GetMapping
    public ResponseEntity<List<PhotoDto>> getAllPhotos() {
        List<PhotoDto> photos = photoService.getPhotosSortedByDate();
        return ResponseEntity.ok(photos);
    }

    /**
     * Récupère une photo par son ID.
     * 
     * @param id l'ID de la photo
     * @return ResponseEntity avec le PhotoDto correspondant
     */
    @GetMapping("/{id}")
    public ResponseEntity<PhotoDto> getPhotoById(@PathVariable String id) {
        PhotoDto photo = photoService.getPhotoById(id);
        if (photo != null) {
            return ResponseEntity.ok(photo);
        }
        return ResponseEntity.notFound().build();
    }

    /**
     * Récupère les photos dans une plage de dates.
     * 
     * @param fromDate date de début (inclusive), format : yyyy-MM-dd'T'HH:mm:ss
     * @param toDate date de fin (inclusive), format : yyyy-MM-dd'T'HH:mm:ss
     * @return ResponseEntity avec la liste des PhotoDto filtrées
     */
    @GetMapping("/by-date")
    public ResponseEntity<List<PhotoDto>> getPhotosByDateRange(
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime fromDate,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime toDate) {
        List<PhotoDto> photos = photoService.getPhotosByDateRange(fromDate, toDate);
        return ResponseEntity.ok(photos);
    }

    /**
     * Télécharge une photo.
     * 
     * @param id l'ID de la photo à télécharger
     * @return ResponseEntity avec le fichier à télécharger
     */
    @GetMapping("/{id}/download")
    public ResponseEntity<Resource> downloadPhoto(@PathVariable String id) {
        PhotoDto photo = photoService.getPhotoById(id);
        
        if (photo == null) {
            return ResponseEntity.notFound().build();
        }
        
        try {
            Path filePath = fileStorageService.load(photo.storedPath());
            Resource resource = new UrlResource(filePath.toUri());
            
            if (resource.exists() || resource.isReadable()) {
                return ResponseEntity.ok()
                        .contentType(MediaType.parseMediaType(photo.mimeType()))
                        .header(HttpHeaders.CONTENT_DISPOSITION, 
                                "attachment; filename=\"" + photo.originalName() + "\"")
                        .body(resource);
            } else {
                return ResponseEntity.notFound().build();
            }
        } catch (IOException e) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).build();
        }
    }

    /**
     * Supprime une photo.
     * 
     * @param id l'ID de la photo à supprimer
     * @return ResponseEntity avec un message de succès ou d'erreur
     */
    @DeleteMapping("/{id}")
    public ResponseEntity<String> deletePhoto(@PathVariable String id) {
        boolean deleted = photoService.deletePhoto(id);
        
        if (deleted) {
            return ResponseEntity.ok("Photo supprimée avec succès");
        }
        return ResponseEntity.notFound().build();
    }

    /**
     * Récupère les métadonnées d'une photo.
     * 
     * @param id l'ID de la photo
     * @return ResponseEntity avec le PhotoDto contenant les métadonnées
     */
    @GetMapping("/{id}/metadata")
    public ResponseEntity<PhotoDto> getPhotoMetadata(@PathVariable String id) {
        PhotoDto photo = photoService.getPhotoById(id);
        if (photo != null) {
            return ResponseEntity.ok(photo);
        }
        return ResponseEntity.notFound().build();
    }

    /**
     * Point d'entrée pour vérifier que le service est opérationnel.
     * 
     * @return ResponseEntity avec un message de statu
     */
    @GetMapping("/status")
    public ResponseEntity<String> status() {
        return ResponseEntity.ok("Service Photo est opérationnel");
    }
}
