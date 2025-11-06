package grouph.core;

import javax.net.ssl.SSLSocket;
import java.util.concurrent.atomic.AtomicLong;

public class UserSession {
    public final SSLSocket socket;
    public String username;
    public volatile Room room;
    public final AtomicLong lastSeenMs = new AtomicLong(System.currentTimeMillis());

    public UserSession(SSLSocket socket) {
        this.socket = socket;
    }
}
