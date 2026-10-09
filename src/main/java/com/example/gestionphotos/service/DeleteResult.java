package com.example.gestionphotos.service;

import java.util.List;

/**
 * Resultat de la suppression multiple de photos.
 * 
 * @param deletedIds liste des IDs des photos supprimées avec succès
 * @param notFoundIds liste des IDs des photos non trouvées
 */
public record DeleteResult(List<String> deletedIds, List<String> notFoundIds) {
}
