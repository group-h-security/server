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

    
}
