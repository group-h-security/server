package grouph.core;

import proto.Op;
import proto.Packet;
import proto.Packets;
import proto.T;

import javax.net.ssl.SSLSocket;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;

public class ClientHandler implements Runnable {
    private final SSLSocket socket;
    private final RoomRegistry registry;
    private final ServerBus bus;
    private volatile boolean running = true;
    private final UserSession session;

    public ClientHandler(SSLSocket socket, RoomRegistry registry, ServerBus bus) {
        this.socket = socket;
        this.registry = registry;
        this.bus = bus;
        this.session = new UserSession(socket);
    }

    @Override
    public void run() {
        // this is probably the most important part of the entire server
        try (InputStream in = socket.getInputStream();
             OutputStream out = socket.getOutputStream()) {

            while (running && !socket.isClosed()) {
                Packet pkt = Packets.read(in);
                if (pkt == null) break; // eof

                // update last time client was seen or sent heartbeat
                session.lastSeenMs.set(System.currentTimeMillis());

                switch (pkt.opcode) {
                    case Op.HEARTBEAT -> {
                        Packets.write(out, Packets.heartbeatAck(System.currentTimeMillis()));
                    }
                    case Op.CREATE_ROOM -> {
                        String code = generateRoomCode(); // gen code
                        Room room = registry.getOrCreateByCode(code); // create room
                        Packets.write(out, Packets.createRoomAck(room.roomId, room.roomCode)); // acknowledge
                    }
                    case Op.JOIN_ROOM -> {
                        String code = pkt.getStr(T.ROOM_CODE); // get room code from pkt
                        String username = pkt.getStr(T.USERNAME); // get username from pkt
                        if (username != null && !username.isEmpty()) {
                            session.username = username; // set username for this session
                        }
                        Room room = registry.getByCode(code);
                        if (room == null) {
                            Packets.write(out, Packets.error(404, "room not found"));
                            break;
                        }
                        bus.userJoined(room, session.username == null ? "system" : session.username); // broadcast BEFORE adding
                        attachToRoom(room); // attach user to room requested to join
                        Packets.write(out, Packets.joinRoomAck(room.roomId, room.roomCode)); // acknowledge
                    }
                    case Op.CHAT_SEND -> {
                        if (session.roomId == null) { // user must be in room to send message
                            Packets.write(out, Packets.error(400, "not in room"));
                            break;
                        }
                        if (!Packets.validateContentLen(pkt)) {
                            Packets.write(out, Packets.error(400, "bad content length"));
                            break;
                        }

                        Room r = registry.getById(session.roomId);
                        DataManager dataManager = new DataManager(r);
                        dataManager.saveMessage(pkt.getStr(T.MESSAGE));

                        // if no username just use anon
                        bus.chat(r, session.username == null ? "anon" : session.username, pkt.getStr(T.MESSAGE));
                    }
                    case Op.LEAVE -> {
                        detachFromRoom();
                        Packets.write(out, Packets.leave());
                        running = false;
                    }
                    case Op.SET_USERNAME -> {
                        session.username = pkt.getStr(T.USERNAME);
                        Packets.write(out, Packets.setUsernameAck(session.username));
                    }
                    case Op.GET_LOGS -> {
                        if(session.roomId == null) {
                            Packets.write(out, Packets.error(400, "not in room"));
                        }
                        Room r = registry.getById(session.roomId);
                        DataManager dataManager = new DataManager(r);
                        String logs = dataManager.getLogs();
                        Packets.write(out, Packets.getLogsAck(logs));
                    }
                    default -> {
                        Packets.write(out, Packets.error(400, "unknown opcode"));
                    }
                }
            }

        } catch (IOException e) {
            throw new RuntimeException(e);
        } finally {
            cleanup();
        }
    }

    private void cleanup() {
        try {
            detachFromRoom();
        } catch (Exception ignored) {
        }
        try {
            socket.close();
        } catch (Exception ignored) {
        }
    }

    private void detachFromRoom() {
        if (session.roomId == null) return;
        Room r = registry.getById(session.roomId);
        if (r != null) {
            r.remove(session);
            bus.userLeft(r, session.username == null ? "system" : session.username);
            registry.removeIfEmpty(r);
        }
        session.roomId = null;
    }

    private void attachToRoom(Room room) {
        if (room == null) return;
        if (session.roomId != null) { // remove from prev room
            Room prev = registry.getById(session.roomId);
            if (prev != null) prev.remove(session);
            registry.removeIfEmpty(prev);
        }
        room.add(session);
        session.roomId = room.roomId;
    }

    private String generateRoomCode() {
        int n = (int) (Math.random() * 1_000_000);
        return String.format("%06d", n); // conv to str
    }
}
