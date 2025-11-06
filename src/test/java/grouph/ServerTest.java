package grouph;

import grouph.core.Server;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import javax.net.ssl.*;
import java.io.FileInputStream;
import java.security.KeyStore;
import java.security.SecureRandom;

import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

public class ServerTest {
    private static Server server;
    private static Thread serverThread;

    private static final String CLIENT_KEYSTORE = "certs/client-keystore.jks";
    private static final String CLIENT_TRUSTSTORE = "certs/client-truststore.jks";
    private static final String PASSWORD = "serverpass"; // TODO: change in prod

    private static final String HOST = "localhost";
    private static final int PORT = 8443;

    @BeforeAll
    static void startServer() throws InterruptedException {
        System.out.println("*** starting server for testing ***");

        // start server in a thread
        serverThread = new Thread(() -> {
            server = new Server();
        });

        serverThread.start();
        // give time for start up
        Thread.sleep(2000);
        System.out.println("*** server ready ***");
    }

    @AfterAll
    static void stopServer() throws InterruptedException {
        System.out.println("\n*** stopping server ***");
        if (server != null) {
            server.stop();
        }
        if (serverThread != null) {
           serverThread.interrupt();
        }
    }

    // litterally a mirror of Server start()
    private SSLSocket createClientSocket() throws Exception {
        // load client keystore
        KeyStore keyStore = KeyStore.getInstance("JKS");
        try (FileInputStream fis = new FileInputStream(CLIENT_KEYSTORE)) {
            keyStore.load(fis, PASSWORD.toCharArray());
        }

        // create keymanager
        KeyManagerFactory kmf = KeyManagerFactory.getInstance(
            KeyManagerFactory.getDefaultAlgorithm()
        );
        kmf.init(keyStore, PASSWORD.toCharArray());

        // load client truststore
        KeyStore trustStore = KeyStore.getInstance("JKS");
        try (FileInputStream fis = new FileInputStream(CLIENT_TRUSTSTORE)) {
            trustStore.load(fis, PASSWORD.toCharArray());
        }

        // verify server cert
        TrustManagerFactory tmf = TrustManagerFactory.getInstance(
            TrustManagerFactory.getDefaultAlgorithm()
        );
        tmf.init(trustStore);


        // create SSLContext
        SSLContext sslContext = SSLContext.getInstance("TLS");
        sslContext.init(
            kmf.getKeyManagers(),
            tmf.getTrustManagers(),
            new SecureRandom()
        );

        // create SSLSocket
        SSLSocketFactory factory = sslContext.getSocketFactory();
        SSLSocket socket = (SSLSocket) factory.createSocket(HOST, PORT);

        return socket;
    }

    @Test
    @DisplayName("mTLS handshake succeeds")
    void testMTLSHandshake() throws Exception {
        System.out.println("TEST 1: testing mTLS handshake...");

        // create client socket with proper certs
        SSLSocket socket = createClientSocket();

        // verify socket is connected
        assertNotNull(socket, "socket should be created");
        assertTrue(socket.isConnected(), "socket should be connected");

        // manually trigger handshake
        socket.startHandshake();

        // verify handshake created valid session
        SSLSession session = socket.getSession();
        assertNotNull(session, "ssl session should exist");
        assertTrue(session.isValid(), "ssl session should be valid");

        // print info
        System.out.println("mTLS handshake successful");
        System.out.println("cipher suite: " + session.getCipherSuite());
        System.out.println("protocol: " + session.getProtocol());

        socket.close();
    }
}
