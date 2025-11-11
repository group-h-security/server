package grouph.core;


import org.bouncycastle.asn1.pkcs.PKCSObjectIdentifiers;
import org.bouncycastle.asn1.x500.X500Name;
import org.bouncycastle.asn1.x509.*;
import org.bouncycastle.jce.provider.BouncyCastleProvider;
import org.bouncycastle.openssl.jcajce.JcaPEMWriter;
import org.bouncycastle.operator.ContentSigner;
import org.bouncycastle.operator.OperatorCreationException;
import org.bouncycastle.operator.jcajce.JcaContentSignerBuilder;
import org.bouncycastle.pkcs.PKCS10CertificationRequest;
import org.bouncycastle.pkcs.PKCS10CertificationRequestBuilder;
import org.bouncycastle.pkcs.jcajce.JcaPKCS10CertificationRequestBuilder;



import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.net.InetAddress;
import java.net.NetworkInterface;
import java.net.SocketException;
import java.net.UnknownHostException;
import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.NoSuchAlgorithmException;
import java.util.Enumeration;
import java.util.HashSet;


public abstract class CsrGenerator {

    public static void generateCsr() {
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
            san.add(new GeneralName(GeneralName.dNSName, "127.0.0.1"));


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

            GeneralNames sanExtension = new GeneralNames(san.toArray(new GeneralName[0]));

            X500Name subject = new X500Name("C=IE,O=Group-H Security,CN=" + fqdn);
            PKCS10CertificationRequestBuilder builder = new JcaPKCS10CertificationRequestBuilder(subject, keyPair.getPublic());
            //Build the csr with the collected info in the extension

            ExtensionsGenerator extGenerator = new ExtensionsGenerator();
            extGenerator.addExtension(Extension.subjectAlternativeName, false, sanExtension);
            KeyUsage ku = new KeyUsage(KeyUsage.digitalSignature | KeyUsage.keyCertSign | KeyUsage.keyEncipherment);
            extGenerator.addExtension(Extension.keyUsage, true, ku);
            ExtendedKeyUsage eku = new ExtendedKeyUsage(KeyPurposeId.id_kp_serverAuth);

            builder.addAttribute(PKCSObjectIdentifiers.pkcs_9_at_extensionRequest, extGenerator.generate());

            // SHA Signer for hashing the cert contents
            ContentSigner signer = new JcaContentSignerBuilder("SHA256withRSA").build(keyPair.getPrivate());
            PKCS10CertificationRequest csr = builder.build((ContentSigner) signer);

            try(JcaPEMWriter w = new JcaPEMWriter(new FileWriter(new File(outDir, "server.key")))) {
                w.writeObject(keyPair.getPrivate());
            }
            try (JcaPEMWriter w = new JcaPEMWriter(new FileWriter(new File(outDir, "server.csr")))) {
                w.writeObject(csr);
            }

            System.out.println("CSR Generated at "+new File(outDir, "server.csr").getAbsolutePath());
            System.out.println("----- CSR DETAILS -----");

// Subject (the DN)
            System.out.println("Subject: " + csr.getSubject());

// Extract public key info (algorithm + key size)
            System.out.println("Public Key Algorithm: " + csr.getSubjectPublicKeyInfo().getAlgorithm().getAlgorithm());

// Extensions (e.g. SAN, KU, EKU)
            try {
                var extensions = csr.getRequestedExtensions();
                if (extensions != null) {
                    var oids = extensions.getExtensionOIDs();
                    for (var oid : oids) {
                        var ext = extensions.getExtension(oid);
                        System.out.println("\nExtension OID: " + oid.getId());
                        System.out.println("  Critical: " + ext.isCritical());

                        if (oid.equals(Extension.subjectAlternativeName)) {
                            GeneralNames gns = GeneralNames.getInstance(ext.getParsedValue());
                            for (GeneralName name : gns.getNames()) {
                                switch (name.getTagNo()) {
                                    case GeneralName.dNSName -> System.out.println("  DNS: " + name.getName());
                                    case GeneralName.iPAddress -> System.out.println("  IP: " + name.getName());
                                    default -> System.out.println("  Other: " + name.getName());
                                }
                            }
                        } else {
                            System.out.println("  Raw: " + ext.getParsedValue());
                        }
                    }
                }
            } catch (Exception e) {
                System.err.println("Could not parse extensions: " + e.getMessage());
            }

            System.out.println("------------------------");

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
    }

    public static void main(String[] args) {
        generateCsr();
    }
}
