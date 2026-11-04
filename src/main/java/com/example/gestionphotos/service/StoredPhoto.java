package com.example.gestionphotos.service;

import java.time.LocalDateTime;

/**
 * Metadonnees d'une photo sauvegardee dans le systeme de stockage.
 * Transportee par la couture entre PhotoService et FileStorageService :
 * PhotoService ne re-analyse plus le chemin stocke pour retrouver
 * l'identifiant et la date de creation.
 *
 * @param id l'identifiant unique de 8 caracteres hexadecimaux
 * @param fileName le nom du fichier sauvegarde (YYYYMMDD_HHmmss_[UUID8].ext)
 * @param storedPath le chemin relatif du fichier sauvegarde
 * @param creationDate la date de creation utilisee pour nommer le fichier
 */
public record StoredPhoto(
    String id,
    String fileName,
    String storedPath,
    LocalDateTime creationDate
) {
}
