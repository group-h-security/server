package grouph;

import java.io.PrintWriter;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public class Room {
    public final UUID roomId; // unique identification number for each room
    public final String roomCode; // 6 digits
    private final Set<User> users = ConcurrentHashMap.newKeySet(); // each room having a number of users

    // to create a room
    public Room(UUID roomId, String roomCode) {
        this.roomId = roomId;
        this.roomCode = roomCode;
    }

    public void add(User u) { users.add(u); } // add user to room
    public void remove(User u) { users.remove(u); } // remove user from room
    public boolean isEmpty() { return users.isEmpty(); } // check if we have any users in the room

    // TODO: barebones impl, might need fixing up later
    public void broadcast(String msg, User from) {
        // for each user in the room
        for (User u : users) {
            try {
                PrintWriter out = new PrintWriter(u.getUserSocket().getOutputStream(), true);
                if (from != null) out.printf("[%s] %s%n", from.getUsername(), msg); // print message from user
                else out.printf("%s%n", msg); // system message
            } catch (Exception ignored) { }
        }
    }
}
