package grouph.core;

import proto.Packets;
import proto.Packet;

import java.util.UUID;

// for system messages
public final class ServerBus {
    public void userJoined(Room room, String username) {
        Packet p = Packets.userJoined(username);
        room.broadcast(p);
    }
    public void userLeft(Room room, String username) {
        Packet p = Packets.userLeft(username);
        room.broadcast(p);
    }
    public void chat(Room room, String username, String message) {
        Packet p = Packets.chatBroadcast(username, message);
        room.broadcast(p);
    }
}
