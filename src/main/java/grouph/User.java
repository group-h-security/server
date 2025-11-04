package grouph;

import javax.net.ssl.SSLSocket;

public class User {
    private SSLSocket userSocket;
    private String username;

    public User(SSLSocket clientSocket, String username) {
        this.userSocket = clientSocket;
        this.username = username;
    }

    public String getUsername() {
        return this.username;
    }

    public SSLSocket getUserSocket() {
        return this.userSocket;
    }
}
