package grouph;

import javax.net.ssl.*;
import java.io.*;
import java.security.*;

public class Server {
    // standard port for https or tls
    private static final int PORT = 8443;
    // path to servers private key and cert
    private static final String KEYSTORE_PATH = "certs/server-keystore.jks";
    // path to servers truststore (all trusted clients)
    private static final String TRUSTSTORE_PATH = "certs/server-truststore.jks";

    // TODO: this is a horrible idea for prod but gotta work with what we got iykyk
    private static final String KEYSTORE_PASSWORD = "serverpass";
    private static final String TRUSTSTORE_PASSWORD = "serverpass";

    private SSLServerSocket serverSocket;
    // today i learned volatile is a way to mark a variable as stored in main memory
    // not just in a threads local cache
    private volatile boolean running;

    // we start the server when constructed
    public Server() {
        start();
    }

    // set up all security components for mTLS
    public void start() {
        try {
            // TODO: figure out steps to complete and write code to complete them
            System.out.println("hello from server");
        } catch (Exception e) {
            System.err.println("failed to start server: " + e.getMessage());
            e.printStackTrace();
        }
    }

    public void stop() {
       return;
    }
}
