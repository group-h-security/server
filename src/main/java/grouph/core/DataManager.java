package grouph.core;

import java.io.File;
import java.io.FileWriter;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.util.Scanner;

public class DataManager {
    private Room room;

    public DataManager(Room room) {
        this.room = room;
    }

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

    public String getLogs() throws IOException {
        if (room == null) {
            throw new IOException("DataManager: room is null");
        }

        File logFile = getDataPath();
        StringBuilder logsBuilder = new StringBuilder();

        try (Scanner mr = new Scanner(logFile)) {
            while (mr.hasNextLine()) {
                logsBuilder.append(mr.nextLine()).append("\n");
            }
        } catch (FileNotFoundException e) {
            System.err.println("Error reading chat logs file");
            e.printStackTrace();
        }

        return logsBuilder.toString();
    }

    public void saveMessage(String message) throws IOException {
        if (room == null) {
            throw new IOException("DataManager: room is null");
        }

        File logFile = getDataPath();
        if (!logFile.exists()) {
            logFile.createNewFile();
        }

        try (FileWriter fw = new FileWriter(logFile, true)) {
            fw.write(message + System.lineSeparator());
        }
    }
}
