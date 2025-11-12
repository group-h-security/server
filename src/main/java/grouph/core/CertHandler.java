package grouph.core;


import org.bouncycastle.asn1.pkcs.PKCSObjectIdentifiers;
import org.bouncycastle.asn1.x500.X500Name;
import org.bouncycastle.asn1.x509.*;
import org.bouncycastle.openssl.jcajce.JcaPEMWriter;
import org.bouncycastle.operator.ContentSigner;
import org.bouncycastle.operator.OperatorCreationException;
import org.bouncycastle.operator.jcajce.JcaContentSignerBuilder;
import org.bouncycastle.pkcs.PKCS10CertificationRequest;
import org.bouncycastle.pkcs.PKCS10CertificationRequestBuilder;
import org.bouncycastle.pkcs.jcajce.JcaPKCS10CertificationRequestBuilder;


import java.io.*;
import java.net.*;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.security.*;
import java.security.cert.CertificateException;
import java.security.cert.CertificateFactory;
import java.security.cert.X509Certificate;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Enumeration;
import java.util.HashSet;


public class CertHandler {
    public static void requestCert(String CAUrl) {
        try {
            File outDir = new File("certs");
            outDir.mkdirs();
            String csrPEM = generateCsr();
            System.out.println(csrPEM);
            Path csrPath = Path.of(outDir.getAbsolutePath(), "server.csr");
            Files.writeString(csrPath, csrPEM);
            System.out.println("CSR Generated at " + new File(outDir, "server.csr").getAbsolutePath());

            String boundary = "---Boundary" + System.currentTimeMillis();

            String body =
                "--" + boundary + "\r\n" +
                    "Content-Disposition: form-data; name=\"csr\"; filename=\"client.csr\"\r\n" +
                    "Content-Type: application/pkcs10\r\n\r\n" +
                    csrPEM + "\r\n" +
                    "--" + boundary + "--\r\n";

            HttpRequest req = HttpRequest.newBuilder()
                .uri(URI.create(CAUrl))
                .timeout(Duration.ofSeconds(10))
                .header("Content-Type", "multipart/form-data; boundary=" + boundary)
                .POST(HttpRequest.BodyPublishers.ofString(body))
                .build();

            HttpClient client = HttpClient.newHttpClient();

            //Get the response
            HttpResponse<String> response = client.send(req, HttpResponse.BodyHandlers.ofString());

            //Check the response
            if (response.statusCode() != 200) {
                throw new IOException("Unexpected response status: " + response.statusCode());
            }

            //Store response in keystore
            KeyStore ks = KeyStore.getInstance("JKS");
            String password = Files.readString(Path.of("stores/keystorePass.txt"), StandardCharsets.UTF_8).trim();
            try (FileInputStream fis = new FileInputStream(new File("stores/server-keystore.jks"))) {
                ks.load(fis, password.toCharArray());
            }


            //Get the private key from the keystore
            Key key = ks.getKey("server", password.toCharArray());
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
            ks.setKeyEntry("server", key, password.toCharArray(), chain.toArray(new X509Certificate[0]));

            try (FileOutputStream out = new FileOutputStream("stores/server-keystore.jks")) {
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

    public static String generateCsr() throws Exception {
        // 0) Debug: confirm working dir and keystore files
        System.out.println("PWD = " + System.getProperty("user.dir"));
        Path passPath = Path.of("stores/keystorePass.txt");
        Path jksPath = Path.of("stores/server-keystore.jks");
        if (!Files.exists(passPath)) throw new FileNotFoundException(passPath + " not found");
        if (!Files.exists(jksPath)) throw new FileNotFoundException(jksPath + " not found");

        Files.createDirectories(Path.of("csrTest"));

        // 1) Load the SAME keypair your script created (JKS)
        char[] pass = Files.readString(passPath).trim().toCharArray();
        KeyStore ks = KeyStore.getInstance("JKS");
        try (InputStream in = Files.newInputStream(jksPath)) {
            ks.load(in, pass);
        }

        String alias = "server";
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

        // 3) Proper server extensions (no keyCertSign)
        ExtensionsGenerator extGen = new ExtensionsGenerator();
        extGen.addExtension(Extension.subjectAlternativeName, false,
            new GeneralNames(sans.toArray(new GeneralName[0])));
        extGen.addExtension(Extension.keyUsage, true,
            new KeyUsage(KeyUsage.digitalSignature | KeyUsage.keyEncipherment));
        extGen.addExtension(Extension.extendedKeyUsage, true,
            new ExtendedKeyUsage(KeyPurposeId.id_kp_serverAuth));
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
        requestCert("http://127.0.0.1:5000/sign");
    }
}
