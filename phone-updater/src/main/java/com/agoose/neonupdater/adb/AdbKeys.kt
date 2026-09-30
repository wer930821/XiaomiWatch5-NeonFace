package com.agoose.neonupdater.adb

import org.bouncycastle.asn1.x500.X500Name
import org.bouncycastle.asn1.x509.BasicConstraints
import org.bouncycastle.asn1.x509.Extension
import org.bouncycastle.asn1.x509.SubjectKeyIdentifier
import org.bouncycastle.asn1.x509.SubjectPublicKeyInfo
import org.bouncycastle.cert.jcajce.JcaX509v3CertificateBuilder
import org.bouncycastle.operator.jcajce.JcaContentSignerBuilder
import java.io.ByteArrayInputStream
import java.io.File
import java.math.BigInteger
import java.security.KeyPairGenerator
import java.security.MessageDigest
import java.security.PrivateKey
import java.security.SecureRandom
import java.security.cert.CertificateFactory
import java.security.cert.X509Certificate
import java.security.spec.PKCS8EncodedKeySpec
import java.util.Date
import java.util.concurrent.TimeUnit

/**
 * The RSA key pair this app identifies itself with when talking to a remote `adbd`.
 *
 * It is the exact equivalent of `~/.android/adbkey` on a desktop: generated once, stored in the
 * app's private storage, and authorised by the watch during pairing. Losing it means pairing again.
 */
object AdbKeys {

    private const val KEY_FILE = "adbkey.pk8"
    private const val CERT_FILE = "adbkey.crt"
    private const val SUBJECT = "CN=NeonFaceUpdater, O=NeonFaceUpdater, C=US"

    class Identity(val privateKey: PrivateKey, val certificate: X509Certificate)

    @Synchronized
    fun loadOrCreate(dir: File): Identity {
        val keyFile = File(dir, KEY_FILE)
        val certFile = File(dir, CERT_FILE)

        if (keyFile.exists() && certFile.exists()) {
            runCatching { return load(keyFile, certFile) }
            // Corrupt or unreadable material: fall through and mint a fresh identity.
            keyFile.delete()
            certFile.delete()
        }
        return create(keyFile, certFile)
    }

    private fun load(keyFile: File, certFile: File): Identity {
        val privateKey = java.security.KeyFactory.getInstance("RSA")
            .generatePrivate(PKCS8EncodedKeySpec(keyFile.readBytes()))
        val certificate = CertificateFactory.getInstance("X.509")
            .generateCertificate(ByteArrayInputStream(certFile.readBytes())) as X509Certificate
        return Identity(privateKey, certificate)
    }

    private fun create(keyFile: File, certFile: File): Identity {
        val generator = KeyPairGenerator.getInstance("RSA")
        generator.initialize(2048, SecureRandom())
        val pair = generator.generateKeyPair()

        val notBefore = Date(System.currentTimeMillis() - TimeUnit.DAYS.toMillis(1))
        val notAfter = Date(System.currentTimeMillis() + TimeUnit.DAYS.toMillis(365L * 20))
        val name = X500Name(SUBJECT)

        val builder = JcaX509v3CertificateBuilder(
            name,
            BigInteger.valueOf(System.currentTimeMillis()),
            notBefore,
            notAfter,
            name,
            pair.public,
        )
        val spki = SubjectPublicKeyInfo.getInstance(pair.public.encoded)
        val keyId = MessageDigest.getInstance("SHA-1").digest(spki.publicKeyData.bytes)
        builder.addExtension(Extension.subjectKeyIdentifier, false, SubjectKeyIdentifier(keyId))
        builder.addExtension(Extension.basicConstraints, true, BasicConstraints(true))

        val signer = JcaContentSignerBuilder("SHA256withRSA").build(pair.private)
        val certificate = CertificateFactory.getInstance("X.509")
            .generateCertificate(ByteArrayInputStream(builder.build(signer).encoded)) as X509Certificate

        keyFile.writeBytes(pair.private.encoded)
        certFile.writeBytes(certificate.encoded)
        return Identity(pair.private, certificate)
    }
}
