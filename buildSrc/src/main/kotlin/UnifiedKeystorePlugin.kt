package grouph.cert

import org.gradle.api.Plugin
import org.gradle.api.Project
import java.io.FileOutputStream
import java.math.BigInteger
import java.security.*
import java.security.cert.X509Certificate
import java.util.*
import org.bouncycastle.asn1.x500.X500Name
import org.bouncycastle.cert.X509v3CertificateBuilder
import org.bouncycastle.cert.jcajce.JcaX509CertificateConverter
import org.bouncycastle.cert.jcajce.JcaX509v3CertificateBuilder
import org.bouncycastle.jce.provider.BouncyCastleProvider
import org.bouncycastle.operator.jcajce.JcaContentSignerBuilder

class UnifiedKeystorePlugin : Plugin<Project> {
    override fun apply(project: Project) {

        // Server keystore task
        project.tasks.register("makeServerKeystore") {
            group = "certs"
            doLast {
                val storesDir = project.file("stores")
                storesDir.mkdirs()

                // Only generate password if it doesn't exist
                val passwordFile = project.file("stores/keystorePass.txt")
                val password = if (passwordFile.exists()) {
                    passwordFile.readText().trim()
                } else {
                    val newPassword = Base64.getEncoder().encodeToString(SecureRandom().generateSeed(24))
                    passwordFile.writeText(newPassword)
                    newPassword
                }

                if (Security.getProvider("BC") == null) {
                    Security.addProvider(BouncyCastleProvider())
                }

                val keyGen = KeyPairGenerator.getInstance("RSA", "BC")
                keyGen.initialize(4096)
                val keyPair = keyGen.generateKeyPair()

                val privateKeyBytes = keyPair.private.encoded
                val privateKeyPem = buildString {
                    appendLine("-----BEGIN PRIVATE KEY-----")
                    appendLine(Base64.getMimeEncoder(64, "\n".toByteArray()).encodeToString(privateKeyBytes))
                    appendLine("-----END PRIVATE KEY-----")
                }
                project.file("stores/server-key.pem").writeText(privateKeyPem)
                println("private key written to stores/server-key.pem")

                val now = Date()
                val until = Date(now.time + 24L * 60 * 60 * 1000)
                val subject = X500Name("C=IE, O=Group-H Security, CN=localhost")
                val serial = BigInteger(64, SecureRandom())

                val certBuilder: X509v3CertificateBuilder = JcaX509v3CertificateBuilder(
                    subject, serial, now, until, subject, keyPair.public
                )
                val signer = JcaContentSignerBuilder("SHA256withRSA").setProvider("BC").build(keyPair.private)
                val cert: X509Certificate = JcaX509CertificateConverter().setProvider("BC").getCertificate(certBuilder.build(signer))

                val ks = KeyStore.getInstance("JKS")
                ks.load(null, password.toCharArray())
                ks.setKeyEntry("server", keyPair.private, password.toCharArray(), arrayOf(cert))

                FileOutputStream(project.file("stores/server-keystore.jks")).use {
                    ks.store(it, password.toCharArray())
                }
                println("server keystore created (stores/server-keystore.jks)")
            }
        }

        // client keystore task
        project.tasks.register("makeClientKeystore") {
            group = "certs"
            doLast {
                val certsDir = project.file("certs")
                certsDir.mkdirs()

                // read pass from server if it exists, both same pass
                val passwordFile = project.file("stores/keystorePass.txt")
                val password = if (passwordFile.exists()) {
                    passwordFile.readText().trim()
                } else {
                    val newPassword = Base64.getEncoder().encodeToString(SecureRandom().generateSeed(24))
                    project.file("stores").mkdirs()
                    passwordFile.writeText(newPassword)
                    newPassword
                }

                // also save in certs dir
                project.file("certs/keystorePass.txt").writeText(password)

                if (Security.getProvider("BC") == null) {
                    Security.addProvider(BouncyCastleProvider())
                }

                val keyGen = KeyPairGenerator.getInstance("RSA", "BC")
                keyGen.initialize(4096)
                val keyPair = keyGen.generateKeyPair()

                val privateKeyBytes = keyPair.private.encoded
                val privateKeyPem = buildString {
                    appendLine("-----BEGIN PRIVATE KEY-----")
                    appendLine(Base64.getMimeEncoder(64, "\n".toByteArray()).encodeToString(privateKeyBytes))
                    appendLine("-----END PRIVATE KEY-----")
                }
                project.file("certs/client-key.pem").writeText(privateKeyPem)
                println("private key written to certs/client-key.pem")

                val now = Date()
                val until = Date(now.time + 24L * 60 * 60 * 1000)
                val subject = X500Name("C=IE, O=Group-H Security, CN=client")
                val serial = BigInteger(64, SecureRandom())

                val certBuilder: X509v3CertificateBuilder = JcaX509v3CertificateBuilder(
                    subject, serial, now, until, subject, keyPair.public
                )
                val signer = JcaContentSignerBuilder("SHA256withRSA").setProvider("BC").build(keyPair.private)
                val cert: X509Certificate = JcaX509CertificateConverter().setProvider("BC").getCertificate(certBuilder.build(signer))

                val ks = KeyStore.getInstance("JKS")
                ks.load(null, password.toCharArray())
                ks.setKeyEntry("client", keyPair.private, password.toCharArray(), arrayOf(cert))

                FileOutputStream(project.file("certs/client-keystore.jks")).use {
                    ks.store(it, password.toCharArray())
                }
                println("client keystore created (certs/client-keystore.jks)")
            }
        }

        // combined task to make both
        project.tasks.register("makeKeystores") {
            group = "certs"
            description = "generate both server and client keystores"
            dependsOn("makeServerKeystore")
        }
    }
}
