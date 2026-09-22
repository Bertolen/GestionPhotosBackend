package com.example.gestionphotos.controller;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.io.IOException;
import java.nio.file.Path;
import java.time.LocalDateTime;
import java.util.List;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import com.example.gestionphotos.dto.PhotoDto;
import com.example.gestionphotos.service.FileStorageService;
import com.example.gestionphotos.exception.DuplicatePhotoException;
import com.example.gestionphotos.exception.MultiplePhotoUploadException;
import com.example.gestionphotos.service.PhotoService;

/**
 * Tests unitaires pour PhotoController.
 * Utilise MockMvc configuré manuellement avec Mockito.
 */
@ExtendWith(MockitoExtension.class)
class PhotoControllerTest {

    private MockMvc mockMvc;

    @Mock
    private PhotoService photoService;

    @Mock
    private FileStorageService fileStorageService;

    private PhotoController photoController;

    private static final String TEST_UUID = "ab123456";
    private static final String TEST_STORED_PATH = "photos/2024/09/22/test_2024-09-22_12-30-45_ab123456.jpg";
    private static final String TEST_ORIGINAL_NAME = "test.jpg";
    private static final String TEST_FILE_NAME = "test_2024-09-22_12-30-45_ab123456.jpg";
    private static final long TEST_SIZE = 1024L;
    private static final String TEST_MIME_TYPE = "image/jpeg";
    private PhotoDto testPhotoDto;

    @BeforeEach
    void setUp() {
        photoController = new PhotoController(photoService, fileStorageService);
        mockMvc = MockMvcBuilders.standaloneSetup(photoController).build();

        testPhotoDto = new PhotoDto(
                TEST_UUID,
                TEST_ORIGINAL_NAME,
                TEST_STORED_PATH,
                TEST_FILE_NAME,
                TEST_SIZE,
                TEST_MIME_TYPE,
                LocalDateTime.now(),
                LocalDateTime.now()
        );
    }


    // ==================== Tests pour POST /upload ====================

    @Test
    void uploadPhoto_shouldReturnPhotoDtoOnSuccess() throws Exception {
        // Arrange
        MockMultipartFile file = new MockMultipartFile(
                "file",
                TEST_ORIGINAL_NAME,
                TEST_MIME_TYPE,
                "test content".getBytes()
        );
        when(photoService.uploadPhoto(any())).thenReturn(testPhotoDto);

        // Act & Assert
        mockMvc.perform(multipart("/api/photos/upload")
                        .file(file))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(TEST_UUID))
                .andExpect(jsonPath("$.originalName").value(TEST_ORIGINAL_NAME))
                .andExpect(jsonPath("$.fileName").value(TEST_FILE_NAME));
    }

    @Test
    void uploadPhoto_shouldReturnBadRequestOnInvalidFile() throws Exception {
        // Arrange
        MockMultipartFile file = new MockMultipartFile(
                "file",
                "",
                TEST_MIME_TYPE,
                new byte[0]
        );
        when(photoService.uploadPhoto(any())).thenThrow(new IllegalArgumentException("Fichier invalide"));

        // Act & Assert
        mockMvc.perform(multipart("/api/photos/upload")
                        .file(file))
                .andExpect(status().isBadRequest());
    }

    @Test
    void uploadPhoto_shouldReturnConflictWhenPhotoAlreadyExists() throws Exception {
        // Arrange
        MockMultipartFile file = new MockMultipartFile(
                "file",
                TEST_ORIGINAL_NAME,
                TEST_MIME_TYPE,
                "test content".getBytes()
        );
        when(photoService.uploadPhoto(any()))
                .thenThrow(new DuplicatePhotoException("La photo existe déjà"));

        // Act & Assert
        mockMvc.perform(multipart("/api/photos/upload")
                        .file(file))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.message").value("La photo existe déjà"));
    }

    @Test
    void uploadPhoto_shouldReturnInternalServerErrorOnIOException() throws Exception {
        // Arrange
        MockMultipartFile file = new MockMultipartFile(
                "file",
                TEST_ORIGINAL_NAME,
                TEST_MIME_TYPE,
                "test content".getBytes()
        );
        when(photoService.uploadPhoto(any())).thenThrow(new IOException("Erreur IO"));

        // Act & Assert
        mockMvc.perform(multipart("/api/photos/upload")
                        .file(file))
                .andExpect(status().isInternalServerError());
    }


    // ==================== Tests pour POST /upload/multiple ====================

    @Test
    void uploadPhotos_shouldReturnListOfPhotoDtos() throws Exception {
        // Arrange
        MockMultipartFile file1 = new MockMultipartFile(
                "files",
                TEST_ORIGINAL_NAME,
                TEST_MIME_TYPE,
                "test content 1".getBytes()
        );
        MockMultipartFile file2 = new MockMultipartFile(
                "files",
                "test2.jpg",
                TEST_MIME_TYPE,
                "test content 2".getBytes()
        );
        
        PhotoDto photo1 = testPhotoDto;
        PhotoDto photo2 = new PhotoDto(
                "cd789012",
                "test2.jpg",
                "photos/2024/09/22/test2_2024-09-22_12-31-00_cd789012.jpg",
                "test2_2024-09-22_12-31-00_cd789012.jpg",
                TEST_SIZE,
                TEST_MIME_TYPE,
                LocalDateTime.now(),
                LocalDateTime.now()
        );
        
        when(photoService.uploadPhotos(any())).thenReturn(List.of(photo1, photo2));

        // Act & Assert
        mockMvc.perform(multipart("/api/photos/upload/multiple")
                        .file(file1)
                        .file(file2))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(2))
                .andExpect(jsonPath("$[0].id").value(TEST_UUID))
                .andExpect(jsonPath("$[1].id").value("cd789012"));
    }

    @Test
    void uploadPhotos_shouldReturnBadRequestOnInvalidFile() throws Exception {
        // Arrange
        MockMultipartFile file = new MockMultipartFile(
                "files",
                "",
                TEST_MIME_TYPE,
                new byte[0]
        );
        when(photoService.uploadPhotos(any())).thenThrow(new IllegalArgumentException("Fichier invalide"));

        // Act & Assert
        mockMvc.perform(multipart("/api/photos/upload/multiple")
                        .file(file))
                .andExpect(status().isBadRequest());
    }

    @Test
    void uploadPhotos_shouldReturnAllResultsAndErrors() throws Exception {
        // Arrange
        MockMultipartFile file = new MockMultipartFile(
                "files",
                TEST_ORIGINAL_NAME,
                TEST_MIME_TYPE,
                "test content".getBytes()
        );
        when(photoService.uploadPhotos(any())).thenThrow(new MultiplePhotoUploadException(
                List.of(testPhotoDto),
                List.of(new DuplicatePhotoException("La photo existe déjà"))));

        // Act & Assert
        mockMvc.perform(multipart("/api/photos/upload/multiple")
                        .file(file))
                .andExpect(status().isMultiStatus())
                .andExpect(jsonPath("$.uploadedPhotos.length()").value(1))
                .andExpect(jsonPath("$.errors.length()").value(1))
                .andExpect(jsonPath("$.errors[0].message").value("La photo existe déjà"));
    }


    // ==================== Tests pour GET / (getAllPhotos) ====================

    @Test
    void getAllPhotos_shouldReturnListOfPhotos() throws Exception {
        // Arrange
        when(photoService.getPhotosSortedByDate()).thenReturn(List.of(testPhotoDto));

        // Act & Assert
        mockMvc.perform(get("/api/photos"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(1))
                .andExpect(jsonPath("$[0].id").value(TEST_UUID));
    }

    @Test
    void getAllPhotos_shouldReturnEmptyList() throws Exception {
        // Arrange
        when(photoService.getPhotosSortedByDate()).thenReturn(List.of());

        // Act & Assert
        mockMvc.perform(get("/api/photos"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(0));
    }


    // ==================== Tests pour GET /{id} ====================

    @Test
    void getPhotoById_shouldReturnPhotoWhenFound() throws Exception {
        // Arrange
        when(photoService.getPhotoById(TEST_UUID)).thenReturn(testPhotoDto);

        // Act & Assert
        mockMvc.perform(get("/api/photos/{id}", TEST_UUID))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(TEST_UUID));
    }

    @Test
    void getPhotoById_shouldReturnNotFoundWhenNotFound() throws Exception {
        // Arrange
        when(photoService.getPhotoById(TEST_UUID)).thenReturn(null);

        // Act & Assert
        mockMvc.perform(get("/api/photos/{id}", TEST_UUID))
                .andExpect(status().isNotFound());
    }


    // ==================== Tests pour GET /by-date ====================

    @Test
    void getPhotosByDateRange_shouldReturnFilteredList() throws Exception {
        // Arrange
        LocalDateTime fromDate = LocalDateTime.of(2024, 1, 1, 0, 0);
        LocalDateTime toDate = LocalDateTime.of(2024, 12, 31, 23, 59);
        when(photoService.getPhotosByDateRange(eq(fromDate), eq(toDate)))
                .thenReturn(List.of(testPhotoDto));

        // Act & Assert
        mockMvc.perform(get("/api/photos/by-date")
                        .param("fromDate", "2024-01-01T00:00:00")
                        .param("toDate", "2024-12-31T23:59:00"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(1));
    }


    // ==================== Tests pour GET /{id}/download ====================

    @Test
    void downloadPhoto_shouldReturnNotFoundWhenPhotoNotFound() throws Exception {
        // Arrange
        when(photoService.getPhotoById(TEST_UUID)).thenReturn(null);

        // Act & Assert
        mockMvc.perform(get("/api/photos/{id}/download", TEST_UUID))
                .andExpect(status().isNotFound());
    }

    @Test
    void downloadPhoto_shouldCallFileStorageServiceWhenPhotoFound() throws Exception {
        // Arrange
        when(photoService.getPhotoById(TEST_UUID)).thenReturn(testPhotoDto);
        Path mockPath = Path.of(TEST_STORED_PATH);
        when(fileStorageService.load(TEST_STORED_PATH)).thenReturn(mockPath);

        // Act
        mockMvc.perform(get("/api/photos/{id}/download", TEST_UUID));

        // Assert - Vérifier que le service a été appelé
        verify(photoService).getPhotoById(TEST_UUID);
        verify(fileStorageService).load(TEST_STORED_PATH);
    }


    // ==================== Tests pour DELETE /{id} ====================

    @Test
    void deletePhoto_shouldReturnSuccessWhenDeleted() throws Exception {
        // Arrange
        when(photoService.deletePhoto(TEST_UUID)).thenReturn(true);

        // Act & Assert
        mockMvc.perform(delete("/api/photos/{id}", TEST_UUID))
                .andExpect(status().isOk())
                .andExpect(content().string("Photo supprimée avec succès"));
    }

    @Test
    void deletePhoto_shouldReturnNotFoundWhenNotFound() throws Exception {
        // Arrange
        when(photoService.deletePhoto(TEST_UUID)).thenReturn(false);

        // Act & Assert
        mockMvc.perform(delete("/api/photos/{id}", TEST_UUID))
                .andExpect(status().isNotFound());
    }


    // ==================== Tests pour GET /{id}/metadata ====================

    @Test
    void getPhotoMetadata_shouldReturnMetadataWhenFound() throws Exception {
        // Arrange
        when(photoService.getPhotoById(TEST_UUID)).thenReturn(testPhotoDto);

        // Act & Assert
        mockMvc.perform(get("/api/photos/{id}/metadata", TEST_UUID))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(TEST_UUID))
                .andExpect(jsonPath("$.originalName").value(TEST_ORIGINAL_NAME));
    }

    @Test
    void getPhotoMetadata_shouldReturnNotFoundWhenNotFound() throws Exception {
        // Arrange
        when(photoService.getPhotoById(TEST_UUID)).thenReturn(null);

        // Act & Assert
        mockMvc.perform(get("/api/photos/{id}/metadata", TEST_UUID))
                .andExpect(status().isNotFound());
    }


    // ==================== Tests pour GET /status ====================

    @Test
    void status_shouldReturnOk() throws Exception {
        // Act & Assert
        mockMvc.perform(get("/api/photos/status"))
                .andExpect(status().isOk())
                .andExpect(content().string("Service Photo est opérationnel"));
    }
}
