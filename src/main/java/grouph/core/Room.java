package grouph.core;

import proto.Packet;

import java.io.OutputStream;
import java.io.PrintWriter;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public class Room {
    public final UUID roomId; // unique identification number for each room
    public final String roomCode; // 6 digits
    private final Set<UserSession> users = ConcurrentHashMap.newKeySet(); // each room having a number of users

    // to create a room
    public Room(UUID roomId, String roomCode) {
        this.roomId = roomId;
        this.roomCode = roomCode;
    }

    public void add(UserSession u) { users.add(u); } // add user to room
    public void remove(UserSession u) { users.remove(u); } // remove user from room
    public boolean isEmpty() { return users.isEmpty(); } // check if we have any users in the room

    // TODO: barebones impl, might need fixing up later
    public void broadcast(Packet pkt) {
        byte[] bytes = pkt.toBytes();
        // for each user in the room
        for (UserSession u : users) {
            try {
                OutputStream outputStream = u.socket.getOutputStream();
                synchronized (outputStream) { // lock!
                    outputStream.write(bytes);
                    outputStream.flush();
                }
            } catch (Exception ignored) { }
        }
    }
}
