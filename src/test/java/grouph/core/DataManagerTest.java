// chatgpt wrote this validate by ryan
package grouph.core;

import org.junit.jupiter.api.*;
import java.io.*;
import java.nio.file.Files;
import java.nio.file.Path;

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
    void testSaveMessage_ThrowsIOException_WhenRoomIsNull() {
        DataManager dm = new DataManager(null);
        assertThrows(IOException.class, () -> dm.saveMessage("hello"));
    }

    @Test
    void testGetLogs_ReadsExistingFile() throws Exception {
        File dataFile = dataManager.getDataPath();

        // Ensure directory exists
        if (!dataFile.getParentFile().exists()) dataFile.getParentFile().mkdirs();

        // Write lines to the file before calling getLogs()
        try (FileWriter writer = new FileWriter(dataFile)) {
            writer.write("log entry 1\n");
            writer.write("log entry 2\n");
        }

        // Now call getLogs() to read it
        String logs = dataManager.getLogs();

        assertTrue(logs.contains("log entry 1"));
        assertTrue(logs.contains("log entry 2"));
    }

    @Test
    void testSaveMessage_CreatesFileAndWrites() throws Exception {
        File dataFile = dataManager.getDataPath();

        // Ensure clean start
        if (dataFile.exists()) Files.delete(dataFile.toPath());

        // Save a message
        dataManager.saveMessage("first message");

        assertTrue(dataFile.exists(), "Data file should be created by saveMessage");

        String logs = dataManager.getLogs();
        assertTrue(logs.contains("first message"));
    }

    @Test
    void testSaveMessage_AppendsToFile() throws Exception {
        File dataFile = dataManager.getDataPath();

        // Start fresh
        if (dataFile.exists()) Files.delete(dataFile.toPath());
        if (!dataFile.getParentFile().exists()) dataFile.getParentFile().mkdirs();

        // Save two messages
        dataManager.saveMessage("message one");
        dataManager.saveMessage("message two");

        // Read file content
        String content = Files.readString(dataFile.toPath());
        assertTrue(content.contains("message one"));
        assertTrue(content.contains("message two"));

        // Also verify getLogs() returns both lines
        String logs = dataManager.getLogs();
        assertTrue(logs.contains("message one"));
        assertTrue(logs.contains("message two"));
    }

    @AfterEach
    void cleanUp() {
        try {
            File dataFile = dataManager.getDataPath();
            if (dataFile.exists()) Files.delete(dataFile.toPath());
            File dir = dataFile.getParentFile();
            if (dir.exists()) dir.delete();
        } catch (Exception ignored) {}
    }
}
