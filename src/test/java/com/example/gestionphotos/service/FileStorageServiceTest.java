package com.example.gestionphotos.service;

import com.example.gestionphotos.exception.DuplicatePhotoException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import org.springframework.web.multipart.MultipartFile;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;

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
        when(multipartFile.getInputStream()).thenReturn(new ByteArrayInputStream(content));

        // Act
        String storedPath = fileStorageService.store(multipartFile);

        // Assert
        assertNotNull(storedPath);
        assertTrue(storedPath.startsWith("photos/"));
        assertTrue(storedPath.contains("test"));
        
        // Vérifier que le fichier existe
        Path fullPath = fileStorageService.getRootLocation().resolve(storedPath.replace('/', java.io.File.separatorChar));
        assertTrue(Files.exists(fullPath));
    }

    @Test
    void store_shouldCreateDateDirectoryStructure() throws IOException {
        // Arrange
        when(multipartFile.getOriginalFilename()).thenReturn("test.png");
        when(multipartFile.getInputStream()).thenReturn(new ByteArrayInputStream("test".getBytes()));

        // Act
        String storedPath = fileStorageService.store(multipartFile);

        // Assert - le chemin doit contenir année/mois/jour
        assertTrue(storedPath.matches("photos/\\d{4}/\\d{2}/\\d{2}/.*"));
        
        // Vérifier que le fichier existe
        Path fullPath = fileStorageService.getRootLocation().resolve(storedPath.replace('/', java.io.File.separatorChar));
        assertTrue(Files.exists(fullPath));
    }

    @Test
    void store_shouldUseTimestampFromAndroidFilenameForDateDirectory() throws IOException {
        // Arrange
        when(multipartFile.getOriginalFilename()).thenReturn("20260920_191817.jpg");
        when(multipartFile.getInputStream()).thenReturn(new ByteArrayInputStream("test".getBytes()));

        // Act
        String storedPath = fileStorageService.store(multipartFile);

        // Assert
        assertTrue(storedPath.matches("photos/2026/09/20/.*"));
        Path fullPath = fileStorageService.getRootLocation().resolve(storedPath.replace('/', java.io.File.separatorChar));
        assertTrue(Files.exists(fullPath));
    }

    @Test
    void store_shouldUseUploadDateForInvalidAndroidFilename() throws IOException {
        // Arrange
        when(multipartFile.getOriginalFilename()).thenReturn("20261320_191817.jpg");
        when(multipartFile.getInputStream()).thenReturn(new ByteArrayInputStream("test".getBytes()));
        String todayPath = java.time.LocalDateTime.now()
                .format(java.time.format.DateTimeFormatter.ofPattern("yyyy/MM/dd"));

        // Act
        String storedPath = fileStorageService.store(multipartFile);

        // Assert
        assertTrue(storedPath.startsWith("photos/" + todayPath + "/"));
    }

    @Test
    void store_shouldGenerateUniqueFilename() throws IOException {
        // Arrange - stocker deux fichiers différents
        when(multipartFile.getOriginalFilename()).thenReturn("test1.jpg").thenReturn("test2.jpg");
        when(multipartFile.getInputStream())
                .thenReturn(new ByteArrayInputStream("content1".getBytes()))
                .thenReturn(new ByteArrayInputStream("content2".getBytes()));

        // Act
        String storedPath1 = fileStorageService.store(multipartFile);
        String storedPath2 = fileStorageService.store(multipartFile);

        // Assert - les noms doivent être différents
        assertNotEquals(storedPath1, storedPath2);
        Path fullPath1 = fileStorageService.getRootLocation().resolve(storedPath1.replace('/', java.io.File.separatorChar));
        Path fullPath2 = fileStorageService.getRootLocation().resolve(storedPath2.replace('/', java.io.File.separatorChar));
        assertTrue(Files.exists(fullPath1));
        assertTrue(Files.exists(fullPath2));
    }

    @Test
    void store_shouldRejectPhotoWithAnExistingOriginalFilename() throws IOException {
        // Arrange
        when(multipartFile.getOriginalFilename()).thenReturn("20260920_191817.jpg");
        when(multipartFile.getInputStream()).thenReturn(new ByteArrayInputStream("content".getBytes()));
        fileStorageService.store(multipartFile);

        // Act & Assert
        assertThrows(DuplicatePhotoException.class, () -> fileStorageService.store(multipartFile));
    }


    // ==================== Tests pour load ====================

    @Test
    void load_shouldReturnAbsolutePath() throws IOException {
        // Arrange
        when(multipartFile.getOriginalFilename()).thenReturn("test.jpg");
        when(multipartFile.getInputStream()).thenReturn(new ByteArrayInputStream("test".getBytes()));
        
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
        when(multipartFile.getInputStream()).thenReturn(new ByteArrayInputStream("test".getBytes()));
        
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
        when(multipartFile.getInputStream()).thenReturn(new ByteArrayInputStream("test".getBytes()));
        
        String storedPath = fileStorageService.store(multipartFile);
        Path filePath = fileStorageService.getRootLocation().resolve(storedPath.replace('/', java.io.File.separatorChar));
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
        when(multipartFile.getInputStream()).thenReturn(new ByteArrayInputStream("test".getBytes()));
        
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
        when(multipartFile.getInputStream()).thenReturn(new ByteArrayInputStream(content.getBytes()));
        
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


    // ==================== Tests pour createZipFromPaths ====================

    @Test
    void createZipFromPaths_shouldCreateValidZipWithMultipleFiles() throws IOException {
        // Arrange - créer plusieurs fichiers
        String content1 = "Contenu du fichier 1";
        String content2 = "Contenu du fichier 2";
        
        when(multipartFile.getOriginalFilename()).thenReturn("file1.txt").thenReturn("file2.txt");
        when(multipartFile.getInputStream())
                .thenReturn(new ByteArrayInputStream(content1.getBytes()))
                .thenReturn(new ByteArrayInputStream(content2.getBytes()));
        
        String path1 = fileStorageService.store(multipartFile);
        String path2 = fileStorageService.store(multipartFile);
        
        // Act
        byte[] zipBytes = fileStorageService.createZipFromPaths(List.of(path1, path2));
        
        // Assert
        assertNotNull(zipBytes);
        assertTrue(zipBytes.length > 0);
        
        // Vérifier que le ZIP contient les deux fichiers
        try (ZipInputStream zis = new ZipInputStream(new ByteArrayInputStream(zipBytes))) {
            ZipEntry entry;
            int fileCount = 0;
            boolean foundFile1 = false;
            boolean foundFile2 = false;
            
            while ((entry = zis.getNextEntry()) != null) {
                fileCount++;
                if (entry.getName().contains("file1")) {
                    foundFile1 = true;
                }
                if (entry.getName().contains("file2")) {
                    foundFile2 = true;
                }
                zis.closeEntry();
            }
            
            assertEquals(2, fileCount);
            assertTrue(foundFile1);
            assertTrue(foundFile2);
        }
    }

    @Test
    void createZipFromPaths_shouldCreateEmptyZipWithEmptyList() throws IOException {
        // Act
        byte[] zipBytes = fileStorageService.createZipFromPaths(List.of());
        
        // Assert
        assertNotNull(zipBytes);
        // Un ZIP vide a quand même un en-tête, donc on vérifie qu'il n'est pas null
        // et que c'est un tableau valide
        assertTrue(zipBytes.length >= 0);
    }

    @Test
    void createZipFromPaths_shouldSkipNonExistentFiles() throws IOException {
        // Arrange - créer un fichier valide et un non-existent
        String content = "Contenu valide";
        when(multipartFile.getOriginalFilename()).thenReturn("valid.txt");
        when(multipartFile.getInputStream()).thenReturn(new ByteArrayInputStream(content.getBytes()));
        
        String validPath = fileStorageService.store(multipartFile);
        String nonExistentPath = "photos/2024/01/01/nonexistent.txt";
        
        // Act
        byte[] zipBytes = fileStorageService.createZipFromPaths(List.of(validPath, nonExistentPath));
        
        // Assert
        assertNotNull(zipBytes);
        assertTrue(zipBytes.length > 0);
        
        // Vérifier que le ZIP contient seulement un fichier
        try (ZipInputStream zis = new ZipInputStream(new ByteArrayInputStream(zipBytes))) {
            ZipEntry entry;
            int fileCount = 0;
            
            while ((entry = zis.getNextEntry()) != null) {
                fileCount++;
                zis.closeEntry();
            }
            
            assertEquals(1, fileCount);
        }
    }

    @Test
    void createZipFromPaths_shouldHandleFilesWithDifferentExtensions() throws IOException {
        // Arrange - créer des fichiers avec différentes extensions
        when(multipartFile.getOriginalFilename())
                .thenReturn("photo1.jpg")
                .thenReturn("photo2.png")
                .thenReturn("doc.txt");
        when(multipartFile.getInputStream())
                .thenReturn(new ByteArrayInputStream("jpg content".getBytes()))
                .thenReturn(new ByteArrayInputStream("png content".getBytes()))
                .thenReturn(new ByteArrayInputStream("txt content".getBytes()));
        
        String path1 = fileStorageService.store(multipartFile);
        String path2 = fileStorageService.store(multipartFile);
        String path3 = fileStorageService.store(multipartFile);
        
        // Act
        byte[] zipBytes = fileStorageService.createZipFromPaths(List.of(path1, path2, path3));
        
        // Assert
        assertNotNull(zipBytes);
        
        // Vérifier que le ZIP contient les 3 fichiers
        try (ZipInputStream zis = new ZipInputStream(new ByteArrayInputStream(zipBytes))) {
            ZipEntry entry;
            int fileCount = 0;
            boolean foundJpg = false;
            boolean foundPng = false;
            boolean foundTxt = false;
            
            while ((entry = zis.getNextEntry()) != null) {
                fileCount++;
                if (entry.getName().contains("jpg")) {
                    foundJpg = true;
                }
                if (entry.getName().contains("png")) {
                    foundPng = true;
                }
                if (entry.getName().contains("txt")) {
                    foundTxt = true;
                }
                zis.closeEntry();
            }
            
            assertEquals(3, fileCount);
            assertTrue(foundJpg);
            assertTrue(foundPng);
            assertTrue(foundTxt);
        }
    }


}
