package grouph;

import javax.net.ssl.SSLSocket;

public class ClientHandler implements Runnable {
    private final SSLSocket socket;

    ClientHandler(SSLSocket socket) {
        this.socket = socket;
    }

    // the task itself, once run is completed, thread is free to do whatever after
    @Override
    public void run() {
        // TODO: important
    }

}
