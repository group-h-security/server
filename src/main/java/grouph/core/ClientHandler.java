package grouph.core;

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

    }
}
