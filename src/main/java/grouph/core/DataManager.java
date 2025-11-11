package grouph.core;

import java.io.File;
import java.io.FileWriter;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.util.Scanner;

import javax.crypto.Cipher;
import javax.crypto.SecretKey;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.SecretKeySpec;
import java.security.SecureRandom;
import java.util.Base64;

/**
 * This class is responsible for the saving and retrieval of chat messages for each individual room
 * On room creation a .txt file is created in the users OS Data cache with the associated room id
 * All messages recieved by the server from clients are encrypted via AES-GCM encryption before being stored in the .txt file
 */

public class DataManager {

    //params: 128 bit auth tag and 12 byte nonce
    private static final int GCM_TAG_BITS = 128;
    private static final int NONCE_BYTES = 12;

    //names for retrieving generated AES key from either the enviroment or system properties
    private static final String ENV_KEY = "CHAT_LOG_AES_KEY";
    private static final String PROP_KEY = "chat.log.aes.key";

    //RNG for nonce creation
    private static final SecureRandom RNG = new SecureRandom();
    //AES key is stored in memory for the lifespan of the JVM
    private static volatile SecretKey aesKey;

    private Room room;

    public DataManager(Room room) {
        this.room = room;
    }

    //.txt file is checked to see if it exists in the users cache and if it doesn't a new file is made
    public File getDataPath() {
        String os = System.getProperty("os.name").toLowerCase();
        String basePath;

        if (os.contains("win")) {
            String appData = System.getenv("APPDATA");
            basePath = appData != null ? appData : System.getProperty("user.home");
        } else if (os.contains("mac")) {
            basePath = System.getProperty("user.home") + "/Library/Application Support";
        } else {
            basePath = System.getProperty("user.home") + "/.local/share";
        }

        File dir = new File(basePath, "grouph");
        if (!dir.exists()) dir.mkdirs();

        return new File(dir, String.format("%s.txt", room.roomId));
    }

    //loads/inits the generated AES key to en/decrypt chat logs
    private static SecretKey getAesKey() {
        if (aesKey == null) {
            synchronized (DataManager.class) {
                if (aesKey == null) {
                    String b64 = System.getProperty(PROP_KEY);
                    if (b64 == null || b64.isEmpty()) {
                        b64 = System.getenv(ENV_KEY);
                    }
                    if (b64 == null || b64.isEmpty()) {
                        throw new IllegalStateException(
                                "Missing AES key for chat logs. " +
                                        "Set -D" + PROP_KEY + " or environment variable " + ENV_KEY);
                    }
                    //ensures valid key length of 128 or 256 bits
                    byte[] keyBytes = Base64.getDecoder().decode(b64);
                    if (keyBytes.length != 16 && keyBytes.length != 32) {
                        throw new IllegalStateException(
                                "AES key must be 16 or 32 bytes (after Base64 decode)");
                    }
                    aesKey = new SecretKeySpec(keyBytes, "AES");
                }
            }
        }
        return aesKey;
    }

    /**
     * Saves a message when called to the room's chatlog .txt using AES-GCM encryption
     * File format per line: <Base64(nonce)>:<Base64(ciphertext+tag)>
     * Each message is stored in cyphertext and given a unique nonce
     */
    public void saveMessage(String message) {
        if (room == null) {
            System.err.println("Cannot save message: room is null");
            return;
        }

        try {
            File file = getDataPath();
            //ensure that log dir and file exists
            if (!file.exists()) {
                File parent = file.getParentFile();
                if (!parent.exists() && !parent.mkdirs()) {
                    System.err.println("Failed to create directories for log file: " + file);
                    return;
                }
                if (!file.createNewFile()) {
                    System.err.println("Failed to create log file: " + file);
                    return;
                }
            }

            //AES-GCM encryption
            SecretKey key = getAesKey();

            //generates a random nonce for the message
            byte[] nonce = new byte[NONCE_BYTES];
            RNG.nextBytes(nonce);

            //init AES-GCM cipher in ENCRYPT MODE
            Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
            GCMParameterSpec spec = new GCMParameterSpec(GCM_TAG_BITS, nonce);
            cipher.init(Cipher.ENCRYPT_MODE, key, spec);

            //encrypts the plaintext
            byte[] ct = cipher.doFinal(message.getBytes(java.nio.charset.StandardCharsets.UTF_8));

            //encodes both the message which is nowi n ciphertext and nonce as base64
            String nonceB64 = Base64.getEncoder().encodeToString(nonce);
            String ctB64 = Base64.getEncoder().encodeToString(ct);

            String line = nonceB64 + ":" + ctB64 + System.lineSeparator();

            //appends the data to the rooms chatlog
            try (FileWriter fw = new FileWriter(file, true)) {
                fw.write(line);
            }
        } catch (Exception e) {
            System.err.println("Failed to save encrypted message: " + e.getMessage());
        }
    }

    /**
     * Retrieves all messages from a chat rooms chat log file
     * since all encrypted messages are held to a format, when found they are decrypted and sipt out
     * If for some reason plaintext is stored in the .txt file it will also spit that out (mostly for testing, can be changed for the
     * purpose of actual cybersec standards :P)
     */
    public String getLogs() throws IOException {
        StringBuilder builder = new StringBuilder();

        if (room == null) {
            throw new IOException("Room is null");
        }

        File file = getDataPath();
        if (!file.exists()) {
            //missing logs are an IO error for callers/tests
            throw new IOException("No logs found for room: " + room.roomId);
        }

        final SecretKey key;
        try {
            key = getAesKey();
        } catch (IllegalStateException e) {
            //if the aes key is missing for some reason catch error and expose as IO problem
            throw new IOException("Cannot decrypt logs: " + e.getMessage(), e);
        }

        try (Scanner scanner = new Scanner(file)) {
            while (scanner.hasNextLine()) {
                String line = scanner.nextLine().trim();
                if (line.isEmpty()) continue;

                //try to decrypt each line, if failed then fall back to plaintext
                String decrypted = tryDecryptLine(line, key);
                builder.append(decrypted).append(System.lineSeparator());
            }
        } catch (Exception e) {
            // surface any fatal read/decrypt issue as IOException
            throw new IOException("Failed to read/decrypt logs", e);
        }

        return builder.toString();
    }

    private String tryDecryptLine(String line, SecretKey key) {
        //encryption format: nonceB64:ctB64
        int idx = line.indexOf(':');
        if (idx <= 0) {
            //if no colon (format) is present then decryption won't occur instead it just spits out plaintext
            return line;
        }

        String nonceB64 = line.substring(0, idx);
        String ctB64 = line.substring(idx + 1);

        try {
            byte[] nonce = Base64.getDecoder().decode(nonceB64);
            byte[] ct = Base64.getDecoder().decode(ctB64);

            Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
            GCMParameterSpec spec = new GCMParameterSpec(GCM_TAG_BITS, nonce);
            cipher.init(Cipher.DECRYPT_MODE, key, spec);

            byte[] pt = cipher.doFinal(ct);
            return new String(pt, java.nio.charset.StandardCharsets.UTF_8);
        } catch (Exception e) {
            //if the decryption fails then spit out ray line instead of losing data
            System.err.println("Failed to decrypt line, returning raw: " + e.getMessage());
            return line;
        }
    }
}
