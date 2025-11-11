package grouph.core;

import org.junit.jupiter.api.*;
import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Base64;

import static org.junit.jupiter.api.Assertions.*;

public class DataManagerTest {
    private RoomRegistry roomRegistry;
    private DataManager dataManager;
    private Room room; // real Room from RoomRegistry

    private String generateRoomCode() {
        int n = (int) (Math.random() * 1_000_000);
        return String.format("%06d", n);
    }

    @BeforeEach
    void setUp() {
        //provide AES key for all tests
        byte[] keyBytes = new byte[16];
        for (int i = 0; i < keyBytes.length; i++) {
            keyBytes[i] = 1;
        }
        String keyB64 = Base64.getEncoder().encodeToString(keyBytes);
        System.setProperty("chat.log.aes.key", keyB64);

        this.roomRegistry = new RoomRegistry();
        String code = generateRoomCode();
        this.room = roomRegistry.getOrCreateByCode(code);
        this.dataManager = new DataManager(room);
    }

    @Test
    void testGetLogs_ThrowsIOException_WhenRoomIsNull() {
        DataManager dm = new DataManager(null);
        assertThrows(IOException.class, dm::getLogs);
    }

    @Test
    void testSaveMessage_DoesNotThrow_WhenRoomIsNull() {
        DataManager dm = new DataManager(null);
        assertDoesNotThrow(() -> dm.saveMessage("hello"));
    }

    @Test
    void testGetLogs_ReadsExistingPlaintextFile() throws Exception {
        File dataFile = dataManager.getDataPath();

        if (!dataFile.getParentFile().exists()) {
            assertTrue(dataFile.getParentFile().mkdirs(), "Failed to create parent directory");
        }

        //writes plain text to test if it spits out regular plaintext (unencrypted)
        try (FileWriter writer = new FileWriter(dataFile)) {
            writer.write("log entry 1\n");
            writer.write("log entry 2\n");
        }

        String logs = dataManager.getLogs();

        assertTrue(logs.contains("log entry 1"));
        assertTrue(logs.contains("log entry 2"));
    }

    @Test
    void testSaveMessage_DecryptedViewIsCorrect() throws Exception {
        File dataFile = dataManager.getDataPath();

        //ensure clean start
        if (dataFile.exists()) {
            Files.delete(dataFile.toPath());
        }

        //save a message (this time it should encrypt)
        dataManager.saveMessage("first message");

        assertTrue(dataFile.exists(), "Data file should be created by saveMessage");

        //getLogs() should return plaintext after it is unencrypted
        String logs = dataManager.getLogs();
        assertTrue(logs.contains("first message"));
    }

    @Test
    void testSaveMessage_AppendsToFile_EncryptedOnDisk_PlaintextViaGetLogs() throws Exception {
        File dataFile = dataManager.getDataPath();

        if (dataFile.exists()) {
            Files.delete(dataFile.toPath());
        }
        if (!dataFile.getParentFile().exists()) {
            assertTrue(dataFile.getParentFile().mkdirs(), "Failed to create parent directory");
        }

        //save two messages both will be AES-GCM encrypted
        dataManager.saveMessage("message one");
        dataManager.saveMessage("message two");

        //raw file content should NOT contain plaintext messages,
        String content = Files.readString(dataFile.toPath());
        assertFalse(content.contains("message one"),
                "Raw log file should not contain plaintext 'message one'");
        assertFalse(content.contains("message two"),
                "Raw log file should not contain plaintext 'message two'");

        //getLogs() should decrypt and return both messages
        String logs = dataManager.getLogs();
        assertTrue(logs.contains("message one"),
                "Decrypted logs should contain 'message one'");
        assertTrue(logs.contains("message two"),
                "Decrypted logs should contain 'message two'");
    }


    //functions the same as old cleanup but makes sure to check if dir is empty befor deletion
    @AfterEach
    void cleanUp() {
        try {
            if (dataManager != null && room != null) {
                File dataFile = dataManager.getDataPath();
                if (dataFile.exists()) {
                    Files.delete(dataFile.toPath());
                }
                File dir = dataFile.getParentFile();
                if (dir.exists()) {
                    //only delete if empty to avoid nuking shared dirs in weird environments
                    String[] children = dir.list();
                    if (children != null && children.length == 0) {
                        dir.delete();
                    }
                }
            }
        } catch (Exception ignored) {
        }
    }
}
