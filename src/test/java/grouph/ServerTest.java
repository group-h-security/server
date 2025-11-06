package grouph;

import grouph.core.Server;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import proto.Op;
import proto.Packet;
import proto.Packets;
import proto.T;

import javax.net.ssl.*;
import java.io.FileInputStream;
import java.io.InputStream;
import java.io.OutputStream;
import java.security.KeyStore;
import java.security.SecureRandom;

import static org.junit.jupiter.api.Assertions.*;

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

    @Test
    @DisplayName("Client creates a room successfully")
    void testClientCreateRoom() throws Exception {
        SSLSocket clientSocket = createClientSocket();
        clientSocket.setSoTimeout(3000); // prevent indefinite blocking
        clientSocket.startHandshake();

        InputStream in = clientSocket.getInputStream();
        OutputStream out = clientSocket.getOutputStream();

        // send CREATE_ROOM packet
        Packet createRoomPkt = Packets.createRoom();
        Packets.write(out, createRoomPkt);

        try {
            Packet ack = Packets.read(in);
            assertNotNull(ack, "Server should respond with CREATE_ROOM_ACK");
            System.out.println("Server responded with opcode: " + ack.opcode);
            // validate roomCode is present and 6 digits
            String roomCode = ack.getStr(T.ROOM_CODE);
            assertNotNull(roomCode, "Room code must be present");
            assertEquals(6, roomCode.length(), "Room code should be 6 digits");
            System.out.println("Room created successfully with code: " + roomCode);
            clientSocket.close();
        } catch (Exception ex) {
            ex.printStackTrace();
            fail("Failed to read server response");
        }
    }
}
