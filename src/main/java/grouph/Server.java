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

            // load servers key store, which contains servers private key and cert
            KeyStore keyStore = KeyStore.getInstance("JKS");
            try(FileInputStream keyStoreFile = new FileInputStream(KEYSTORE_PATH)) {
                keyStore.load(keyStoreFile, KEYSTORE_PASSWORD.toCharArray());
            }
            System.out.println("loaded server keystore");

            // use KeyManager to present server cert to clients
            KeyManagerFactory kmf = KeyManagerFactory.getInstance(KeyManagerFactory.getDefaultAlgorithm());
            kmf.init(keyStore, KEYSTORE_PASSWORD.toCharArray());
            System.out.println("init key manager");

            // load server truststore
            KeyStore trustStore = KeyStore.getInstance("JKS");
            try(FileInputStream trustStoreFile = new FileInputStream(TRUSTSTORE_PATH)) {
                trustStore.load(trustStoreFile, TRUSTSTORE_PASSWORD.toCharArray());
            }
            System.out.println("loaded server trust store (trusted clients)");

            // TrustManager validates incoming client certificates,
            // is this client certificate signed by someone i trust?
            TrustManagerFactory tmf = TrustManagerFactory.getInstance(TrustManagerFactory.getDefaultAlgorithm());
            tmf.init(trustStore);
            System.out.println("init trustmanager (verify client certs)");

            // main security config object (SSLContext)
            // we combine both KeyManger and TrustManager
            SSLContext sslContext = SSLContext.getInstance("TLS");
            sslContext.init(
                kmf.getKeyManagers(),   // how to present our identity
                tmf.getTrustManagers(), // how to verify client identity
                new SecureRandom()      // rng for crypto
            );
            System.out.println("created SSLContext");

            // here we create our SSLServerSocket, the important bit
            SSLServerSocketFactory factory = sslContext.getServerSocketFactory();
            serverSocket = (SSLServerSocket) factory.createServerSocket(PORT);

            // the line that makes it "mutual TLS"
            // without this line only the server would be authenticated (TLS not mTLS)
            // with this both server and client must prove identity
            serverSocket.setNeedClientAuth(true);

            running = true;
            System.out.printf( // programming java for 3 years and only know i realise printf is a thing
                """
                server started on port %d
                waiting for client connection
                """, PORT
            );



        } catch (Exception e) {
            System.err.println("failed to start server: " + e.getMessage());
            e.printStackTrace();
        }
    }

    public void stop() {
       return;
    }
}
