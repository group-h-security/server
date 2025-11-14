package grouph.core;

import org.bouncycastle.asn1.pkcs.PKCSObjectIdentifiers;
import org.bouncycastle.asn1.x500.X500Name;
import org.bouncycastle.asn1.x509.*;
import org.bouncycastle.asn1.x509.Extension;
import org.bouncycastle.operator.ContentSigner;
import org.bouncycastle.operator.jcajce.JcaContentSignerBuilder;
import org.bouncycastle.pkcs.PKCS10CertificationRequest;
import org.bouncycastle.pkcs.jcajce.JcaPKCS10CertificationRequestBuilder;
import org.bouncycastle.openssl.jcajce.JcaPEMWriter;

import javax.net.ssl.SSLContext;
import javax.net.ssl.TrustManagerFactory;
import java.io.*;
import java.net.*;
import java.net.http.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.security.*;
import java.security.cert.*;
import java.time.Duration;
import java.util.*;

public class CertHandler {
    public static void requestServerCert(String CAUrl) {
        requestCert(CAUrl, "server", Path.of("stores/server-keystore.jks"),
            Path.of("stores/keystorePass.txt"), Path.of("certs"));
    }

    public static void requestClientCert(String CAUrl) {
        requestCert(CAUrl, "client", Path.of("certs/client-keystore.jks"),
            Path.of("certs/keystorePass.txt"), Path.of("certs"));
    }

    private static SSLContext buildSSLContext(Path truststorePath, String password) {
        char[] pass = password.toCharArray();
        try {
            KeyStore trustStore = KeyStore.getInstance("JKS");
            try(InputStream in = Files.newInputStream(truststorePath)) {
                trustStore.load(in, pass);
            }
            catch (CertificateException | NoSuchAlgorithmException e) {
                throw new RuntimeException(e);
            }

            TrustManagerFactory tmf = TrustManagerFactory.getInstance(TrustManagerFactory.getDefaultAlgorithm());
            tmf.init(trustStore);

            SSLContext sslContext = SSLContext.getInstance("TLS");
            sslContext.init(null, tmf.getTrustManagers(), null);

            return sslContext;
        }
        catch (KeyStoreException | IOException e) {
            throw new RuntimeException(e);
        }
        catch (NoSuchAlgorithmException e) {
            throw new RuntimeException(e);
        }
        catch (KeyManagementException e) {
            throw new RuntimeException(e);
        }


    }

    public static void requestCert(String CAUrl, String alias, Path keystorePath, Path passPath, Path outDir) {
        try {
            File outDirFile = outDir.toFile();
            outDirFile.mkdirs();

            Path csrPath = Path.of(outDirFile.getAbsolutePath(), alias + ".csr");
            String csrPEM;

            // If CSR already exists, reuse it
            if (Files.exists(csrPath)) {
                csrPEM = Files.readString(csrPath);
                System.out.println("Using existing CSR: " + csrPath.toAbsolutePath());
            } else {
                csrPEM = generateCsr(keystorePath, passPath, alias);
                System.out.println(csrPEM);
                Files.writeString(csrPath, csrPEM);
                System.out.println("CSR Generated at " + csrPath.toAbsolutePath());
            }

            String boundary = "---Boundary" + System.currentTimeMillis();

            String body =
                "--" + boundary + "\r\n" +
                    "Content-Disposition: form-data; name=\"csr\"; filename=\"" + alias + ".csr\"\r\n" +
                    "Content-Type: application/pkcs10\r\n\r\n" +
                    csrPEM + "\r\n" +
                    "--" + boundary + "--\r\n";

            HttpRequest req = HttpRequest.newBuilder()
                .uri(URI.create(CAUrl))
                .timeout(Duration.ofSeconds(10))
                .header("Content-Type", "multipart/form-data; boundary=" + boundary)
                .POST(HttpRequest.BodyPublishers.ofString(body))
                .build();


            SSLContext sslContext = null;
            //-O. Injecting HTTPS Here
            if(alias.equals("client")) {
                sslContext = buildSSLContext(Path.of("certs", "client-truststore.jks"), "changeit");
            }
            else {
                sslContext = buildSSLContext(Path.of("stores", "server-truststore.jks"), "changeit");
            }

            HttpClient client = HttpClient.newBuilder()
                    .sslContext(sslContext)
                    .build();
            //Get the response
            HttpResponse<String> response = client.send(req, HttpResponse.BodyHandlers.ofString());

            //Check the response
            if (response.statusCode() != 200) {
                throw new IOException("Unexpected response status: " + response.statusCode());
            }

            //Store response in keystore
            KeyStore ks = KeyStore.getInstance("JKS");
            String password = Files.readString(passPath, StandardCharsets.UTF_8).trim();
            try (FileInputStream fis = new FileInputStream(keystorePath.toFile())) {
                ks.load(fis, password.toCharArray());
            }



            //Get the private key from the keystore
            Key key = ks.getKey(alias, password.toCharArray());

            if (key instanceof PrivateKey) {
                System.out.println("Private key algorithm: " + key.getAlgorithm());
            }

            X509Certificate leaf = (X509Certificate) ks.getCertificate(alias);
            PublicKey pub = leaf.getPublicKey();
            System.out.println("Leaf certificate public key algorithm: " + pub.getAlgorithm());
            if (pub instanceof java.security.interfaces.RSAPublicKey) {
                System.out.println("RSA key size: " + ((java.security.interfaces.RSAPublicKey) pub).getModulus().bitLength());
            }


            //if here for validity

            // Parsing the returned PEM into a chain
            CertificateFactory cf = CertificateFactory.getInstance("X.509");
            ArrayList<X509Certificate> chain = new ArrayList<>();
            for (String cert : response.body().split("(?=-----BEGIN )")) {
                cert = cert.trim();
                if (cert.isEmpty()) continue;
                try (ByteArrayInputStream bais =
                         new ByteArrayInputStream(cert.getBytes(StandardCharsets.UTF_8))) {
                    chain.add((X509Certificate) cf.generateCertificate(bais));
                }
            }
            if (chain.isEmpty()) throw new IOException("No certificate found");

            //replace the dummy entry in the keystore with this valid one
            ks.setKeyEntry(alias, key, password.toCharArray(), chain.toArray(new X509Certificate[0]));


            try (FileOutputStream out = new FileOutputStream(keystorePath.toFile())) {
                ks.store(out, password.toCharArray());
            }

            System.out.println("The keystore has been updated");

        } catch (IOException e) {
            throw new RuntimeException(e);
        } catch (InterruptedException e) {
            throw new RuntimeException(e);
        } catch (KeyStoreException e) {
            throw new RuntimeException(e);
        } catch (CertificateException e) {
            throw new RuntimeException(e);
        } catch (NoSuchAlgorithmException e) {
            throw new RuntimeException(e);
        } catch (UnrecoverableKeyException e) {
            throw new RuntimeException(e);
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    public static void importCATruststore(Path truststorePath, Path passPath, Path caCertPath) {
        try {
            // importing ca cert into trust store

            if (!Files.exists(caCertPath)) {
                throw new FileNotFoundException("ca certs not found at: " + caCertPath);
            }

            // load ca cert
            CertificateFactory cf = CertificateFactory.getInstance("X.509");
            X509Certificate caCert;
            try (FileInputStream fis = new FileInputStream(caCertPath.toFile())) {
                caCert = (X509Certificate) cf.generateCertificate(fis);
            }

            // load truststore
            KeyStore trustStore = KeyStore.getInstance("JKS");
            String password = Files.readString(passPath, StandardCharsets.UTF_8).trim();

            File tsFile = truststorePath.toFile();
            if (tsFile.exists()) {
                try (FileInputStream fis = new FileInputStream(tsFile)) {
                    trustStore.load(fis, password.toCharArray());
                }
            } else {
                trustStore.load(null, password.toCharArray());
            }

            // import ca cert
            trustStore.setCertificateEntry("ca", caCert);

            // save truststore
            try (FileOutputStream fos = new FileOutputStream(truststorePath.toFile())) {
                trustStore.store(fos, password.toCharArray());
            }
            System.out.println("ca cert imported into: " + truststorePath);
        } catch (Exception e) {
            throw new RuntimeException("failed to import ca cert into truststore", e);
        }
    }

    public static String generateCsr(Path jksPath, Path passPath, String alias) throws Exception {
        // 0) Debug: confirm working dir and keystore files
        System.out.println("PWD = " + System.getProperty("user.dir"));
        if (!Files.exists(passPath)) throw new FileNotFoundException(passPath + " not found");
        if (!Files.exists(jksPath)) throw new FileNotFoundException(jksPath + " not found");

        Files.createDirectories(Path.of("csrTest"));

        // 1) Load the SAME keypair your script created (JKS)
        char[] pass = Files.readString(passPath).trim().toCharArray();
        KeyStore ks = KeyStore.getInstance("JKS");
        try (InputStream in = Files.newInputStream(jksPath)) {
            ks.load(in, pass);
        }

        Key key = ks.getKey(alias, pass);
        if (key == null) throw new KeyStoreException("No private key for alias '" + alias + "'");
        PrivateKey priv = (PrivateKey) key;

        X509Certificate dummy = (X509Certificate) ks.getCertificate(alias);
        if (dummy == null) throw new KeyStoreException("No certificate for alias '" + alias + "'");
        PublicKey pub = dummy.getPublicKey();

        // 2) Subject + SANs (don’t let SAN discovery abort the whole method)
        String fqdn = InetAddress.getLocalHost().getCanonicalHostName();
        String host = InetAddress.getLocalHost().getHostName();

        java.util.LinkedHashSet<GeneralName> sans = new java.util.LinkedHashSet<>();
        sans.add(new GeneralName(GeneralName.dNSName, host));
        sans.add(new GeneralName(GeneralName.dNSName, fqdn));
        sans.add(new GeneralName(GeneralName.dNSName, "localhost"));
        sans.add(new GeneralName(GeneralName.iPAddress, "127.0.0.1"));

        try {
            Enumeration<NetworkInterface> ifs = NetworkInterface.getNetworkInterfaces();
            while (ifs.hasMoreElements()) {
                NetworkInterface nif = ifs.nextElement();
                Enumeration<InetAddress> addrs = nif.getInetAddresses();
                while (addrs.hasMoreElements()) {
                    String ip = addrs.nextElement().getHostAddress();
                    if (ip.contains(".")) sans.add(new GeneralName(GeneralName.iPAddress, ip));
                }
            }
        } catch (SocketException se) {
            System.err.println("WARN: Could not enumerate interfaces: " + se.getMessage());
            // continue; SAN already has basics
        }

        X500Name subject = new X500Name("C=IE,O=Group-H Security,CN=" + fqdn);
        JcaPKCS10CertificationRequestBuilder builder =
            new JcaPKCS10CertificationRequestBuilder(subject, pub);

        // permissions
        ExtensionsGenerator extGen = new ExtensionsGenerator();
        extGen.addExtension(Extension.subjectAlternativeName, false,
            new GeneralNames(sans.toArray(new GeneralName[0])));
        extGen.addExtension(Extension.keyUsage, true,
            new KeyUsage(KeyUsage.digitalSignature | KeyUsage.keyEncipherment));

        if (alias.equalsIgnoreCase("client")) {
            extGen.addExtension(Extension.extendedKeyUsage, true,
                new ExtendedKeyUsage(KeyPurposeId.id_kp_clientAuth));
        } else {
            extGen.addExtension(Extension.extendedKeyUsage, true,
                new ExtendedKeyUsage(KeyPurposeId.id_kp_serverAuth));
        }

        builder.addAttribute(PKCSObjectIdentifiers.pkcs_9_at_extensionRequest, extGen.generate());

        ContentSigner signer = new JcaContentSignerBuilder("SHA256withRSA").build(priv);
        PKCS10CertificationRequest csr = builder.build(signer);

        StringWriter sw = new StringWriter();
        try (JcaPEMWriter w = new JcaPEMWriter(sw)) {
            w.writeObject(csr);
        }
        return sw.toString();
    }

    public static void main(String[] args) {
        try {

            Path rootCaPath = Path.of("certs/rootCert.crt");

            // import ca to server truststore
            importCATruststore(
                    Path.of("stores/server-truststore.jks"),
                    Path.of("stores/trustPass.txt"),
                    rootCaPath
            );

            requestServerCert("https://127.0.0.1:5000/sign");
         //   requestClientCert("https://127.0.0.1:5000/sign");





            /*
            // import ca into client trust store
            importCATruststore(
                Path.of("certs/client-truststore.jks"),
                Path.of("certs/keystorePass.txt"),
                rootCaPath
            );*/

            System.out.println("mTLS setup complete, client and server should be able to communicate");
        } catch (Exception e) {
            e.printStackTrace();
        }
    }
}
