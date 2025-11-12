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
            Path csrPath = Path.of(outDir.getAbsolutePath(), "server.csr");
            Files.writeString(csrPath, csrPEM);
            System.out.println("CSR Generated at "+new File(outDir, "server.csr").getAbsolutePath());


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
                    .header("Content-Type", "multipart/form-data; boundary="+boundary)
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
            try(FileInputStream fis = new FileInputStream(new File("stores/server-keystore.jks"))) {
                ks.load(fis, password.toCharArray());
            }


            //Get the private key from the keystore
            Key key = ks.getKey("server",password.toCharArray());
            //if here for validity

            // Parsing the returned PEM into a chain
            CertificateFactory cf = CertificateFactory.getInstance("X.509");
            ArrayList<X509Certificate> chain = new ArrayList<>();
            for(String cert : response.body().split("(?=-----BEGIN )")) {
                cert = cert.trim();
                if(cert.isEmpty()) continue;;
                try (ByteArrayInputStream bais =
                        new ByteArrayInputStream(cert.getBytes(StandardCharsets.UTF_8))) {
                    chain.add((X509Certificate) cf.generateCertificate(bais));
                }
            }
            if(chain.isEmpty()) throw new IOException("No certificate found");

            //replace the dummy entry in the keystore with this valid one
            ks.setKeyEntry("server", key, password.toCharArray(), chain.toArray(new X509Certificate[0]));

            try (FileOutputStream out = new FileOutputStream("stores/server-keystore.jks")) {
                ks.store(out, password.toCharArray());
            }

            System.out.println("The keystore has been updated");

        }
        catch (IOException e) {
            throw new RuntimeException(e);
        } catch (InterruptedException e) {
            throw new RuntimeException(e);
        } catch (KeyStoreException e) {
            throw new RuntimeException(e);
        }
        catch (CertificateException e) {
            throw new RuntimeException(e);
        }
        catch (NoSuchAlgorithmException e) {
            throw new RuntimeException(e);
        } catch (UnrecoverableKeyException e) {
            throw new RuntimeException(e);
        }


    }

    public static String generateCsr() {
        try {
            File outDir = new File("csrTest");
            outDir.mkdirs();

            KeyPairGenerator keyGen = KeyPairGenerator.getInstance("RSA");
            keyGen.initialize(2048);
            KeyPair keyPair = keyGen.generateKeyPair();

            //Setting up the host identifiers - may pass this as configurable strings later
            String fqdn = InetAddress.getLocalHost().getCanonicalHostName();
            String hostName = InetAddress.getLocalHost().getHostName();

            //Building the SAN for the CSR: DNS + IP + LocalHost
            HashSet<GeneralName> san = new HashSet<>();
            san.add(new GeneralName(GeneralName.dNSName, hostName));
            san.add(new GeneralName(GeneralName.dNSName, fqdn));
            san.add(new GeneralName(GeneralName.dNSName, "localhost"));
            san.add(new GeneralName(GeneralName.iPAddress, "127.0.0.1"));


            //Grabbing all the machine's IPs from all interfaces
            Enumeration<NetworkInterface> interfaces = NetworkInterface.getNetworkInterfaces();
            //Iterating through interfaces - not sure how this will play with docker
            while(interfaces.hasMoreElements()) {
                NetworkInterface networkInterface = interfaces.nextElement();
                Enumeration<InetAddress> inetAddresses = networkInterface.getInetAddresses();
                //Iterating through the addresses on that interface
                while(inetAddresses.hasMoreElements()) {
                    InetAddress a = inetAddresses.nextElement();
                    String ip = a.getHostAddress();
                    if(ip.contains(".")) { //Filter for IPv4
                        san.add(new GeneralName(GeneralName.iPAddress, ip));
                    }

                }
            }



            X500Name subject = new X500Name("C=IE,O=Group-H Security,CN=" + fqdn);
            PKCS10CertificationRequestBuilder builder = new JcaPKCS10CertificationRequestBuilder(subject, keyPair.getPublic());
            //Build the csr with the collected info in the extension

            // Building the extensions for the csr - extra info for the cer - we need the SAN identity at minimum,
            // Rest of configuration is for extra security.
            ExtensionsGenerator extGenerator = new ExtensionsGenerator();

            GeneralNames sanExtension = new GeneralNames(san.toArray(new GeneralName[0]));
            KeyUsage ku = new KeyUsage(KeyUsage.digitalSignature | KeyUsage.keyCertSign | KeyUsage.keyEncipherment);
            ExtendedKeyUsage eku = new ExtendedKeyUsage(KeyPurposeId.id_kp_serverAuth);


            extGenerator.addExtension(Extension.subjectAlternativeName, false, sanExtension);
            extGenerator.addExtension(Extension.keyUsage, true, ku);
            extGenerator.addExtension(Extension.extendedKeyUsage, true, eku);

            builder.addAttribute(PKCSObjectIdentifiers.pkcs_9_at_extensionRequest, extGenerator.generate());

            // SHA Signer for hashing the cert contents
            ContentSigner signer = new JcaContentSignerBuilder("SHA256withRSA").build(keyPair.getPrivate());
            PKCS10CertificationRequest csr = builder.build((ContentSigner) signer);

            try(JcaPEMWriter w = new JcaPEMWriter(new FileWriter(new File(outDir, "server.key")))) {
                w.writeObject(keyPair.getPrivate());
            }
            try (StringWriter strWtr = new StringWriter();
                JcaPEMWriter pemWtr = new JcaPEMWriter(strWtr)) {
                pemWtr.writeObject(csr);
                pemWtr.flush();
                return strWtr.toString();
            }


        }


        catch (NoSuchAlgorithmException e) {

        }
        catch (UnknownHostException uhe) {

        }
        catch (SocketException se) {

        }
        catch (IOException ioe) {

        }
        catch (OperatorCreationException e) {
            throw new RuntimeException(e);
        }

        return null;
    }

    public static void main(String[] args) {
        requestCert("http://127.0.0.1:5000/sign");
    }
}
