package com.example.gestionphotos.dto;

import java.time.LocalDateTime;

/**
 * DTO pour transporter les métadonnées d'une photo.
 * Utilisé pour les réponses API et l'affichage dans le frontend.
 */
public record PhotoDto(
    String id,
    String originalName,
    String storedPath,
    String fileName,
    long size,
    String mimeType,
    LocalDateTime uploadDate,
    LocalDateTime creationDate
) {}
