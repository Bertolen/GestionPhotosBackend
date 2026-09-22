package com.example.gestionphotos.service;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.when;

/**
 * Tests unitaires pour FileStorageService.
 */
class FileStorageServiceTest {

    @TempDir
    Path tempDir;

    private FileStorageService fileStorageService;

    @Mock
    private MultipartFile multipartFile;

    @BeforeEach
    void setUp() throws IOException {
        MockitoAnnotations.openMocks(this);
        // Créer un répertoire de stockage temporaire
        Path storagePath = tempDir.resolve("storage");
        Files.createDirectories(storagePath);
        
        // FileStorageService attend un chemin de stockage dans son constructeur
        // On utilise System.setProperty pour simuler @Value("${app.storage.path}")
        System.setProperty("app.storage.path", storagePath.toString());
        
        fileStorageService = new FileStorageService(storagePath.toString());
    }

    // ==================== Tests pour store ====================

    @Test
    void store_shouldSaveFileAndReturnRelativePath() throws IOException {
        // Arrange
        String originalFilename = "test.jpg";
        byte[] content = "test content".getBytes();
        
        when(multipartFile.getOriginalFilename()).thenReturn(originalFilename);
        when(multipartFile.getInputStream()).thenReturn(new InputStream() {
            private int index = 0;
            @Override
            public int read() {
                return index < content.length ? content[index++] : -1;
            }
        });

        // Act
        String storedPath = fileStorageService.store(multipartFile);

        // Assert
        assertNotNull(storedPath);
        assertTrue(storedPath.startsWith("photos/"));
        assertTrue(storedPath.contains("test"));
        
        // Vérifier que le fichier existe
        Path fullPath = fileStorageService.getRootLocation().resolve(storedPath);
        assertTrue(Files.exists(fullPath));
    }

    @Test
    void store_shouldCreateDateDirectoryStructure() throws IOException {
        // Arrange
        when(multipartFile.getOriginalFilename()).thenReturn("test.png");
        when(multipartFile.getInputStream()).thenReturn(new InputStream() {
            private int index = 0;
            private final byte[] content = "test".getBytes();
            @Override
            public int read() {
                return index < content.length ? content[index++] : -1;
            }
        });

        // Act
        String storedPath = fileStorageService.store(multipartFile);

        // Assert - le chemin doit contenir année/mois/jour
        assertTrue(storedPath.matches("photos/\\d{4}/\\d{2}/\\d{2}/.*"));
    }

    @Test
    void store_shouldGenerateUniqueFilename() throws IOException {
        // Arrange - stocker deux fois le même fichier
        when(multipartFile.getOriginalFilename()).thenReturn("test.jpg");
        when(multipartFile.getInputStream())
                .thenReturn(createInputStream("content1"))
                .thenReturn(createInputStream("content2"));

        // Act
        String storedPath1 = fileStorageService.store(multipartFile);
        String storedPath2 = fileStorageService.store(multipartFile);

        // Assert - les noms doivent être différents
        assertNotEquals(storedPath1, storedPath2);
        assertTrue(Files.exists(fileStorageService.getRootLocation().resolve(storedPath1)));
        assertTrue(Files.exists(fileStorageService.getRootLocation().resolve(storedPath2)));
    }


    // ==================== Tests pour load ====================

    @Test
    void load_shouldReturnAbsolutePath() throws IOException {
        // Arrange
        when(multipartFile.getOriginalFilename()).thenReturn("test.jpg");
        when(multipartFile.getInputStream()).thenReturn(createInputStream("test"));
        
        String storedPath = fileStorageService.store(multipartFile);

        // Act
        Path result = fileStorageService.load(storedPath);

        // Assert
        assertNotNull(result);
        assertTrue(result.isAbsolute());
        assertTrue(Files.exists(result));
    }

    @Test
    void load_shouldReturnNormalizedPath() throws IOException {
        // Arrange
        when(multipartFile.getOriginalFilename()).thenReturn("test.jpg");
        when(multipartFile.getInputStream()).thenReturn(createInputStream("test"));
        
        String storedPath = fileStorageService.store(multipartFile);

        // Act
        Path result = fileStorageService.load(storedPath);

        // Assert
        assertEquals(result, result.normalize());
    }


    // ==================== Tests pour delete ====================

    @Test
    void delete_shouldRemoveFile() throws IOException {
        // Arrange
        when(multipartFile.getOriginalFilename()).thenReturn("test.jpg");
        when(multipartFile.getInputStream()).thenReturn(createInputStream("test"));
        
        String storedPath = fileStorageService.store(multipartFile);
        Path filePath = fileStorageService.getRootLocation().resolve(storedPath);
        assertTrue(Files.exists(filePath));

        // Act
        fileStorageService.delete(storedPath);

        // Assert
        assertFalse(Files.exists(filePath));
    }

    @Test
    void delete_shouldNotThrowWhenFileDoesNotExist() throws IOException {
        // Arrange
        String nonExistentPath = "photos/2024/01/01/nonexistent.jpg";

        // Act & Assert
        assertDoesNotThrow(() -> fileStorageService.delete(nonExistentPath));
    }


    // ==================== Tests pour exists ====================

    @Test
    void exists_shouldReturnTrueWhenFileExists() throws IOException {
        // Arrange
        when(multipartFile.getOriginalFilename()).thenReturn("test.jpg");
        when(multipartFile.getInputStream()).thenReturn(createInputStream("test"));
        
        String storedPath = fileStorageService.store(multipartFile);

        // Act
        boolean result = fileStorageService.exists(storedPath);

        // Assert
        assertTrue(result);
    }

    @Test
    void exists_shouldReturnFalseWhenFileDoesNotExist() {
        // Act
        boolean result = fileStorageService.exists("photos/nonexistent.jpg");

        // Assert
        assertFalse(result);
    }


    // ==================== Tests pour getFileSize ====================

    @Test
    void getFileSize_shouldReturnCorrectSize() throws IOException {
        // Arrange
        String content = "test content for size";
        when(multipartFile.getOriginalFilename()).thenReturn("test.jpg");
        when(multipartFile.getInputStream()).thenReturn(createInputStream(content));
        
        String storedPath = fileStorageService.store(multipartFile);

        // Act
        long size = fileStorageService.getFileSize(storedPath);

        // Assert
        assertEquals(content.length(), size);
    }


    // ==================== Tests pour getRootLocation ====================

    @Test
    void getRootLocation_shouldReturnStoragePath() {
        // Act
        Path root = fileStorageService.getRootLocation();

        // Assert
        assertNotNull(root);
        assertTrue(root.isAbsolute());
        assertTrue(root.toString().contains("storage"));
    }


    // ==================== Tests pour les méthodes utilitaires ====================

    @Test
    void getFileExtension_shouldReturnExtensionWithDot() {
        assertEquals(".jpg", fileStorageService.getFileExtension("test.jpg"));
        assertEquals(".png", fileStorageService.getFileExtension("image.png"));
        assertEquals(".txt", fileStorageService.getFileExtension("file.tar.txt"));
        assertEquals(".unknown", fileStorageService.getFileExtension("noextension"));
        assertEquals(".unknown", fileStorageService.getFileExtension(""));
        assertEquals(".unknown", fileStorageService.getFileExtension(null));
    }

    @Test
    void removeExtension_shouldRemoveLastExtension() {
        assertEquals("test", fileStorageService.removeExtension("test.jpg"));
        assertEquals("image", fileStorageService.removeExtension("image.png"));
        assertEquals("file.tar", fileStorageService.removeExtension("file.tar.txt"));
        assertEquals("noextension", fileStorageService.removeExtension("noextension"));
        assertEquals("unknown", fileStorageService.removeExtension(""));
        assertEquals("unknown", fileStorageService.removeExtension(null));
    }


    // ==================== Helper methods ====================

    private InputStream createInputStream(String content) {
        return new InputStream() {
            private int index = 0;
            private final byte[] bytes = content.getBytes();
            @Override
            public int read() {
                return index < bytes.length ? bytes[index++] : -1;
            }
        };
    }
}
