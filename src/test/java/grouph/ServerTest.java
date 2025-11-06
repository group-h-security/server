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
    @DisplayName("mTLS handshake, room creation, joining, and messaging")
    void testFullFlow() throws Exception {
        SSLSocket client1 = createClientSocket();
        SSLSocket client2 = createClientSocket();

        client1.setSoTimeout(5000);
        client2.setSoTimeout(5000);

        // manually trigger handshake
        client1.startHandshake();
        client2.startHandshake();

        // verify handshake created valid session
        SSLSession session = client1.getSession();
        assertNotNull(session, "ssl session should exist");
        assertTrue(session.isValid(), "ssl session should be valid");

        // print info
        System.out.println("mTLS handshake successful");
        System.out.println("cipher suite: " + session.getCipherSuite());
        System.out.println("protocol: " + session.getProtocol());

        try (InputStream in1 = client1.getInputStream();
             OutputStream out1 = client1.getOutputStream();
             InputStream in2 = client2.getInputStream();
             OutputStream out2 = client2.getOutputStream()) {

            // create room
            Packet createRoomPkt = Packets.createRoom();
            Packets.write(out1, createRoomPkt);

            // read create room ack
            Packet createAck = Packets.read(in1);
            assertNotNull(createAck, "Server should respond with CREATE_ROOM_ACK");
            assertEquals(Op.CREATE_ROOM_ACK, createAck.opcode, "Expected CREATE_ROOM_ACK opcode");

            // validate roomCode is present and 6 digits
            String roomCode = createAck.getStr(T.ROOM_CODE);
            assertNotNull(roomCode, "Room code must be present");
            assertEquals(6, roomCode.length(), "Room code should be 6 digits");
            System.out.println("Room created successfully with code: " + roomCode);

            // set username for person who create room (they already joined but under anon)
            Packet setUsername = Packets.setUsername("client1");
            Packets.write(out1, setUsername);

            // make sure username was set successfully
            Packet setUsernameAck = Packets.read(in1);
            assertNotNull(setUsernameAck);
            assertEquals(Op.SET_USERNAME_ACK, setUsernameAck.opcode);

            // client 2 joins room
            Packet joinRoomPkt = Packets.joinRoom(roomCode).addStr(T.USERNAME, "client2");
            Packets.write(out2, joinRoomPkt);

            // validate joined room
            Packet joinAck = Packets.read(in2);
            assertNotNull(joinAck, "Server should respond with JOIN_ROOM_ACK");
            assertEquals(Op.JOIN_ROOM_ACK, joinAck.opcode, "Expected JOIN_ROOM_ACK opcode");
            assertEquals(roomCode, joinAck.getStr(T.ROOM_CODE), "Joined room code should match created room");
            System.out.println("Joined room successfully with code: " + roomCode);

            // client 1 should recieve USER_JOINED for client 2
            Packet userJoined = Packets.read(in1);
            assertNotNull(userJoined);
            assertEquals(Op.USER_JOINED, userJoined.opcode);

            String username2 = userJoined.getStr(T.USERNAME);
            assertNotNull(username2);

            // client 2 send message
            String message = "client 2 says hello";
            Packet chatPkt = Packets.chatSend(message).addStr(T.USERNAME, "client2"); // client2 username
            Packets.write(out2, chatPkt);

            // client 1 should recieve chat broadcast
            Packet broadcast = Packets.read(in1);
            assertNotNull(broadcast);
            assertEquals(Op.CHAT_BROADCAST, broadcast.opcode);

            String receivedUser = broadcast.getStr(T.USERNAME); // get the user who sent message
            String receivedMessage = broadcast.getStr(T.MESSAGE); // get the message that the user sent

            assertEquals("client2", receivedUser); // make sure client2 is the username who sent message
            assertEquals(message, receivedMessage); // make sure the message is the same

            // client 2 also receives their own message broadcast
            Packet broadcastSelf = Packets.read(in2);
            assertNotNull(broadcastSelf);
            assertEquals(Op.CHAT_BROADCAST, broadcastSelf.opcode);
            System.out.println("Client 2 received their own message broadcast");

            // test heartbeat
            Packet heartbeat = Packets.heartbeat();
            Packets.write(out1, heartbeat);

            Packet heartbeatAck = Packets.read(in1);
            assertNotNull(heartbeatAck);
            assertEquals(Op.HEARTBEAT_ACK, heartbeatAck.opcode);
            System.out.println("Heartbeat successful");

            // client 2 leaves room
            Packet leavePkt = Packets.leave();
            Packets.write(out2, leavePkt);

            // client 1 should receive USER_LEFT broadcast for client 2
            Packet userLeft1 = Packets.read(in1);
            assertNotNull(userLeft1);
            assertEquals(Op.USER_LEFT, userLeft1.opcode);
            System.out.println("Client 1 received USER_LEFT notification");

            // client 2 receives leave acknowledgment (no USER_LEFT because they were removed before broadcast)
            Packet leaveAck = Packets.read(in2);
            assertNotNull(leaveAck);
            assertEquals(Op.LEAVE, leaveAck.opcode);
            System.out.println("Client 2 left successfully");
        } finally {
            client1.close();
            client2.close();
        }
    }

    @Test
    @DisplayName("Join nonexistent room returns error")
    void testJoinNonexistentRoom() throws Exception {
        SSLSocket client = createClientSocket();
        client.setSoTimeout(5000);
        client.startHandshake();

        try (InputStream in = client.getInputStream();
             OutputStream out = client.getOutputStream()) {

            // try to join room that doesnt exist
            Packet joinRoomPkt = Packets.joinRoom("999999");
            Packets.write(out, joinRoomPkt);

            Packet response = Packets.read(in);
            assertNotNull(response);
            assertEquals(Op.ERROR, response.opcode);

            long errorCode = response.getU32(T.ERROR_CODE);
            assertEquals(404, errorCode, "Should return 404 for room not found");
            System.out.println("Error received as expected: " + response.getStr(T.REASON));
        } finally {
            client.close();
        }
    }

    @Test
    @DisplayName("Send message without joining room returns error")
    void testChatWithoutRoom() throws Exception {
        SSLSocket client = createClientSocket();
        client.setSoTimeout(5000);
        client.startHandshake();

        try (InputStream in = client.getInputStream();
             OutputStream out = client.getOutputStream()) {

            // try to send message without being in room
            Packet chatPkt = Packets.chatSend("hello world");
            Packets.write(out, chatPkt);

            Packet response = Packets.read(in);
            assertNotNull(response);
            assertEquals(Op.ERROR, response.opcode);

            long errorCode = response.getU32(T.ERROR_CODE);
            assertEquals(400, errorCode, "Should return 400 for not in room");
            System.out.println("Error received as expected: " + response.getStr(T.REASON));
        } finally {
            client.close();
        }
    }

    @Test
    @DisplayName("Unknown opcode returns error")
    void testUnknownOpcode() throws Exception {
        SSLSocket client = createClientSocket();
        client.setSoTimeout(5000);
        client.startHandshake();

        try (InputStream in = client.getInputStream();
             OutputStream out = client.getOutputStream()) {

            // send packet with invalid opcode
            Packet invalidPkt = new Packet();
            Packets.write(out, invalidPkt);

            Packet response = Packets.read(in);
            assertNotNull(response);
            assertEquals(Op.ERROR, response.opcode);

            long errorCode = response.getU32(T.ERROR_CODE);
            assertEquals(400, errorCode, "Should return 400 for unknown opcode");
            System.out.println("Error received as expected: " + response.getStr(T.REASON));
        } finally {
            client.close();
        }
    }
}
