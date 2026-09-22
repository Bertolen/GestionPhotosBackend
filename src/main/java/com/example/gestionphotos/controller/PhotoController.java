package com.example.gestionphotos.controller;

import java.io.IOException;
import java.nio.file.Path;
import java.time.LocalDateTime;
import java.util.List;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.core.io.Resource;
import org.springframework.core.io.UrlResource;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import com.example.gestionphotos.dto.PhotoDto;
import com.example.gestionphotos.service.FileStorageService;
import com.example.gestionphotos.exception.DuplicatePhotoException;
import com.example.gestionphotos.exception.MultiplePhotoUploadException;
import com.example.gestionphotos.service.PhotoService;

/**
* Controller REST pour la gestion des photos.
* Expose les endpoints pour l'upload, le téléchargement, la liste et la suppression des photos.
*/
@RestController
@RequestMapping("/api/photos")
public class PhotoController {
    
    private static final Logger logger = LoggerFactory.getLogger(PhotoController.class);
    
    private final PhotoService photoService;
    private final FileStorageService fileStorageService;
    
    @Autowired
    public PhotoController(PhotoService photoService, FileStorageService fileStorageService) {
        this.photoService = photoService;
        this.fileStorageService = fileStorageService;
        logger.info("PhotoController initialisé");
    }
    
    /**
    * Upload une photo.
    * 
    * @param file le fichier à uploader
    * @return ResponseEntity avec le PhotoDto de la photo sauvegardée
    */
    @PostMapping("/upload")
    public ResponseEntity<?> uploadPhoto(@RequestParam("file") MultipartFile file) {
        logger.info("Appel de uploadPhoto");
        logger.debug("Paramètre file : null={}, empty={}", file == null, file != null && file.isEmpty());
        if (file != null) {
            logger.debug("File details - name: {}, size: {}, contentType: {}", 
            file.getOriginalFilename(), file.getSize(), file.getContentType());
        }
        
        try {
            PhotoDto photoDto = photoService.uploadPhoto(file);
            logger.info("Photo uploadée avec succès, ID: {}", photoDto.id());
            return ResponseEntity.ok(photoDto);
        } catch (IllegalArgumentException e) {
            logger.error("Erreur de validation : {}", e.getMessage());
            return ResponseEntity.badRequest().body(null);
        } catch (DuplicatePhotoException e) {
            logger.info("Photo ignorée car déjà présente : {}", e.getMessage());
            return ResponseEntity.status(HttpStatus.CONFLICT)
                    .body(java.util.Map.of("message", e.getMessage()));
        } catch (IOException e) {
            logger.error("Erreur IO : {}", e.getMessage(), e);
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
    public ResponseEntity<?> uploadPhotos(@RequestParam("files") MultipartFile[] files) {
        logger.info("Appel de uploadPhotos");
        logger.debug("Paramètre files : null={}, length={}", files == null, files != null ? files.length : 0);
        if (files != null) {
            for(MultipartFile f : files){
                logger.debug("File details - name: {}, size: {}, contentType: {}", 
                f.getOriginalFilename(), f.getSize(), f.getContentType());
            }
        }
        try {
            List<PhotoDto> photoDtos = photoService.uploadPhotos(files);
            logger.info("Photos uploadée avec succès, ID: {}", photoDtos.stream().map(p -> p.id()).toArray());
            return ResponseEntity.ok(photoDtos);
        } catch (MultiplePhotoUploadException e) {
            logger.warn("{} erreur(s) pendant l'upload multiple", e.getErrors().size());
            List<java.util.Map<String, String>> errors = e.getErrors().stream()
                    .map(error -> java.util.Map.of(
                            "type", error.getClass().getSimpleName(),
                            "message", error.getMessage() != null ? error.getMessage() : "Erreur inconnue"))
                    .toList();
            return ResponseEntity.status(HttpStatus.MULTI_STATUS)
                    .body(java.util.Map.of(
                            "uploadedPhotos", e.getUploadedPhotos(),
                            "errors", errors));
        } catch (IllegalArgumentException e) {
            logger.error("Erreur de validation : {}", e.getMessage());
            return ResponseEntity.badRequest().body(null);
        } catch (DuplicatePhotoException e) {
            logger.info("Photo ignorée car déjà présente : {}", e.getMessage());
            return ResponseEntity.status(HttpStatus.CONFLICT)
                    .body(java.util.Map.of("message", e.getMessage()));
        } catch (IOException e) {
            logger.error("Erreur IO : {}", e.getMessage(), e);
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
        logger.info("Appel de getAllPhotos");
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
        logger.info("Appel de getPhotoById");
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
            logger.info("Appel de getPhotosByDateRange");
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
            logger.info("Appel de downloadPhoto");
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
                logger.info("Appel de deletePhoto");
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
                logger.info("Appel de getPhotoMetadata");
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
                logger.info("Appel de status");
                return ResponseEntity.ok("Service Photo est opérationnel");
            }
        }
        