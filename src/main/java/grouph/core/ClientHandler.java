package grouph.core;

import proto.Op;
import proto.Packet;
import proto.Packets;

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
        try(InputStream in = socket.getInputStream();
            OutputStream out = socket.getOutputStream()) {

            while(running && !socket.isClosed()) {
                Packet pkt = Packets.read(in);
                if(pkt == null) break; // eof

                // update last time client was seen or sent heartbeat
                session.lastSeenMs.set(System.currentTimeMillis());

                switch(pkt.opcode) {
                    case Op.HEARTBEAT -> {

                    }
                    case Op.CREATE_ROOM-> {

                    }
                    case Op.JOIN_ROOM -> {

                    }
                    case Op.CHAT_SEND -> {

                    }
                    case Op.LEAVE -> {

                    }
                    default -> {

                    }
                }
            }

        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }
}
