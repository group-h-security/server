package grouph.core;

import org.junit.jupiter.api.Test;

import java.nio.file.Files;
import java.nio.file.Path;
import java.security.SecureRandom;
import java.util.Base64;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

public class DataManagerEncryptedTest {

    @Test
    void messagesAreEncryptedOnDiskAndDecryptedByGetLogs() throws Exception {
        //configures AES key: random 128-bit key
        byte[] keyBytes = new byte[16];
        new SecureRandom().nextBytes(keyBytes);
        String keyB64 = Base64.getEncoder().encodeToString(keyBytes);
        System.setProperty("chat.log.aes.key", keyB64);

        //creates a room
        Room room = new Room(UUID.randomUUID(), "TEST");
        DataManager dm = new DataManager(room);

        String msg = "hello world";

        //save message (encrypted)
        dm.saveMessage(msg);

        //check log file exists
        Path path = dm.getDataPath().toPath();
        assertTrue(Files.exists(path), "Log file should exist");

        String raw = Files.readString(path);
        assertFalse(raw.isEmpty(), "Log file should not be empty");

        //check that plaintext is not visible on disk
        assertFalse(raw.contains(msg),
                "Plaintext message should NOT appear in the log file (should be AES encrypted)");

        //check that getLogs() returns the original plaintext and there is no mutation
        String logs = dm.getLogs();
        assertTrue(logs.contains(msg),
                "Decrypted logs should contain the original message");

        Files.deleteIfExists(path);
        Files.deleteIfExists(path.getParent());
    }
}

