package grouph;

import javax.net.ssl.SSLSocket;

public class User {
    public final SSLSocket userSocket;
    public final String username;

    public User(SSLSocket clientSocket, String username) {
        this.userSocket = clientSocket;
        this.username = username;
    }
}
