package com.acta.recorder;

import android.content.Context;
import android.os.Build;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.io.OutputStream;
import java.security.KeyFactory;
import java.security.KeyPairGenerator;
import java.security.PrivateKey;
import java.security.PublicKey;
import java.security.SecureRandom;
import java.security.cert.Certificate;
import java.security.cert.CertificateFactory;
import java.security.spec.PKCS8EncodedKeySpec;
import java.util.Date;
import java.util.Random;

import android.sun.security.x509.AlgorithmId;
import android.sun.security.x509.CertificateAlgorithmId;
import android.sun.security.x509.CertificateExtensions;
import android.sun.security.x509.CertificateIssuerName;
import android.sun.security.x509.CertificateSerialNumber;
import android.sun.security.x509.CertificateSubjectName;
import android.sun.security.x509.CertificateValidity;
import android.sun.security.x509.CertificateVersion;
import android.sun.security.x509.CertificateX509Key;
import android.sun.security.x509.KeyIdentifier;
import android.sun.security.x509.PrivateKeyUsageExtension;
import android.sun.security.x509.SubjectKeyIdentifierExtension;
import android.sun.security.x509.X500Name;
import android.sun.security.x509.X509CertImpl;
import android.sun.security.x509.X509CertInfo;

import io.github.muntashirakon.adb.AbsAdbConnectionManager;

/**
 * Cliente ADB propio de Acta ("copia de Shizuku"): genera su clave y certificado,
 * se empareja con la depuración inalámbrica del propio móvil y se conecta, sin
 * depender de ninguna app de terceros. Todo el ADB ocurre dentro del teléfono.
 */
public class ActaAdb extends AbsAdbConnectionManager {

    private static ActaAdb instance;

    private PrivateKey privateKey;
    private Certificate certificate;

    static synchronized ActaAdb get(Context c) throws Exception {
        if (instance == null) instance = new ActaAdb(c.getApplicationContext());
        return instance;
    }

    private ActaAdb(Context context) throws Exception {
        setApi(Build.VERSION.SDK_INT);
        setTimeout(12, java.util.concurrent.TimeUnit.SECONDS);
        privateKey = readKey(context);
        certificate = readCert(context);
        if (privateKey == null || certificate == null) {
            generate(context);
        }
    }

    private void generate(Context context) throws Exception {
        KeyPairGenerator kpg = KeyPairGenerator.getInstance("RSA");
        kpg.initialize(2048, SecureRandom.getInstance("SHA1PRNG"));
        java.security.KeyPair pair = kpg.generateKeyPair();
        PublicKey publicKey = pair.getPublic();
        privateKey = pair.getPrivate();

        String subject = "CN=Acta";
        String algorithm = "SHA256withRSA";
        long notAfterMs = System.currentTimeMillis() + 10L * 365 * 24 * 60 * 60 * 1000;
        CertificateExtensions ext = new CertificateExtensions();
        ext.set("SubjectKeyIdentifier", new SubjectKeyIdentifierExtension(
                new KeyIdentifier(publicKey).getIdentifier()));
        X500Name x500 = new X500Name(subject);
        Date notBefore = new Date();
        Date notAfter = new Date(notAfterMs);
        ext.set("PrivateKeyUsage", new PrivateKeyUsageExtension(notBefore, notAfter));
        CertificateValidity validity = new CertificateValidity(notBefore, notAfter);
        X509CertInfo info = new X509CertInfo();
        info.set("version", new CertificateVersion(2));
        info.set("serialNumber", new CertificateSerialNumber(new Random().nextInt() & Integer.MAX_VALUE));
        info.set("algorithmID", new CertificateAlgorithmId(AlgorithmId.get(algorithm)));
        info.set("subject", new CertificateSubjectName(x500));
        info.set("key", new CertificateX509Key(publicKey));
        info.set("validity", validity);
        info.set("issuer", new CertificateIssuerName(x500));
        info.set("extensions", ext);
        X509CertImpl cert = new X509CertImpl(info);
        cert.sign(privateKey, algorithm);
        certificate = cert;

        writeKey(context, privateKey);
        writeCert(context, certificate);
    }

    @Override
    protected PrivateKey getPrivateKey() {
        return privateKey;
    }

    @Override
    protected Certificate getCertificate() {
        return certificate;
    }

    @Override
    protected String getDeviceName() {
        return "Acta";
    }

    // ---- almacenamiento ----

    private static File keyFile(Context c) { return new File(c.getFilesDir(), "adb_key.der"); }
    private static File certFile(Context c) { return new File(c.getFilesDir(), "adb_cert.pem"); }

    private static PrivateKey readKey(Context c) {
        File f = keyFile(c);
        if (!f.exists()) return null;
        try (InputStream in = new FileInputStream(f)) {
            byte[] data = new byte[(int) f.length()];
            int off = 0, n;
            while (off < data.length && (n = in.read(data, off, data.length - off)) > 0) off += n;
            return KeyFactory.getInstance("RSA").generatePrivate(new PKCS8EncodedKeySpec(data));
        } catch (Exception e) {
            return null;
        }
    }

    private static Certificate readCert(Context c) {
        File f = certFile(c);
        if (!f.exists()) return null;
        try (InputStream in = new FileInputStream(f)) {
            return CertificateFactory.getInstance("X.509").generateCertificate(in);
        } catch (Exception e) {
            return null;
        }
    }

    private static void writeKey(Context c, PrivateKey key) throws Exception {
        try (OutputStream out = new FileOutputStream(keyFile(c))) {
            out.write(key.getEncoded()); // PKCS#8
        }
    }

    private static void writeCert(Context c, Certificate cert) throws Exception {
        try (OutputStream out = new FileOutputStream(certFile(c))) {
            out.write("-----BEGIN CERTIFICATE-----\n".getBytes());
            out.write(android.util.Base64.encode(cert.getEncoded(), android.util.Base64.DEFAULT));
            out.write("-----END CERTIFICATE-----\n".getBytes());
        }
    }
}
