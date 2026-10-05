package com.example.gestionphotos;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.annotation.ComponentScan;

/**
 * Classe principale de l'application Spring Boot pour la gestion de photos.
 * Cette application permet d'uploader, visualiser, télécharger et supprimer des photos
 * avec un tri automatique par date et heure dans le système de stockage.
 */
@SpringBootApplication
@ComponentScan(basePackages = {"com.example.gestionphotos"})
public class GestionPhotosApplication {

    public static void main(String[] args) {
        SpringApplication.run(GestionPhotosApplication.class, args);
        System.out.println("\n" +
                "████████████████████████████████████████████████████\n" +
                "██ Application GestionPhotos démarrée avec succès ! ██\n" +
                "██                                                       ██\n" +
                "██   Endpoints disponibles :                             ██\n" +
                "██   - GET    /api/photos              Liste des photos  ██\n" +
                "██   - POST   /api/photos/upload       Upload une photo   ██\n" +
                "██   - POST   /api/photos/upload/multi Upload multiples    ██\n" +
                "██   - GET    /api/photos/{id}/download Télécharger une photo██\n" +
                "██   - DELETE /api/photos/{id}          Supprimer une photo██\n" +
                "██                                                       ██\n" +
                "██   Les photos sont stockées dans : ./photos-storage   ██\n" +
                "██                                                       ██\n" +
                "████████████████████████████████████████████████\n");
    }
}
