package de.privateaim.node_message_broker.common;

import io.netty.handler.ssl.SslContext;
import io.netty.handler.ssl.SslContextBuilder;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import javax.net.ssl.SSLContext;
import javax.net.ssl.TrustManagerFactory;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.nio.file.Paths;
import java.security.KeyManagementException;
import java.security.KeyStore;
import java.security.KeyStoreException;
import java.security.NoSuchAlgorithmException;
import java.security.cert.CertificateException;
import java.security.cert.CertificateFactory;
import java.security.cert.X509Certificate;
import java.util.List;

/**
 * Configuration parts for SSL functionality.
 */
@Slf4j
@Configuration
public class CommonSslSpringConfig {

    // Taken from: https://docs.oracle.com/en/java/javase/11/docs/specs/security/standard-names.html#trustmanagerfactory-algorithms
    private static final String TRUST_MANAGER_FACTORY_ALGORITHM = "PKIX";
    private static final String DEFAULT_TRUSTSTORE_PASSWORD = "changeit";
    private static final String TRUST_STORE_PROPERTY = "javax.net.ssl.trustStore";
    private static final String TRUST_STORE_PASSWORD_PROPERTY = "javax.net.ssl.trustStorePassword";
    private static final String JAVA_HOME_PROPERTY = "java.home";
    private static final String CERTIFICATE_FACTORY_TYPE = "X.509";
    private static final String SSL_CONTEXT_PROTOCOL = "TLS";

    @Value("${app.security.additionalTrustedCertsFile}")
    private String additionalTrustedCertsFile;

    @Qualifier("COMMON_NETTY_SSL_CONTEXT")
    @Bean
    SslContext sslContextNetty(@Qualifier("COMMON_TRUST_MANAGER_FACTORY") TrustManagerFactory tmf) throws IOException {
        return SslContextBuilder.forClient()
                .trustManager(tmf)
                .build();
    }

    @Qualifier("COMMON_JAVA_SSL_CONTEXT")
    @Bean
    SSLContext sslContextJava(@Qualifier("COMMON_TRUST_MANAGER_FACTORY") TrustManagerFactory tmf) {
        try {
            var ctx = SSLContext.getInstance(SSL_CONTEXT_PROTOCOL);
            ctx.init(null, tmf.getTrustManagers(), null);

            return ctx;
        } catch (NoSuchAlgorithmException e) {
            // cannot happen
            throw new RuntimeException(e);
        } catch (KeyManagementException e) {
            throw new RuntimeException(e);
        }
    }

    @Qualifier("COMMON_TRUST_MANAGER_FACTORY")
    @Bean
    TrustManagerFactory sharedTrustManagerFactory() throws IOException, KeyStoreException {
        var additionalCerts = loadAdditionalCerts();
        return getTrustManagerFactory(additionalCerts);
    }

    private TrustManagerFactory getTrustManagerFactory(List<X509Certificate> additionalCerts) throws IOException,
            KeyStoreException {
        var trustStore = System.getProperty(TRUST_STORE_PROPERTY);

        var ks = (trustStore != null && !trustStore.isEmpty())
                ? loadKeyStore(new File(trustStore), getTrustStorePassword())
                : loadDefaultKeyStore();

        for (X509Certificate cert : additionalCerts) {
            ks.setCertificateEntry(cert.getSubjectX500Principal().getName(), cert);
        }

        try {
            var tmf = TrustManagerFactory.getInstance(TRUST_MANAGER_FACTORY_ALGORITHM);
            tmf.init(ks);
            return tmf;
        } catch (NoSuchAlgorithmException e) {
            // cannot happen because each JRE implementation is supposed to offer the used algorithm
            throw new RuntimeException(e);
        }
    }

    private List<X509Certificate> loadAdditionalCerts() {
        if (additionalTrustedCertsFile == null || additionalTrustedCertsFile.isEmpty()) {
            log.warn("no additional trusted certificate specified, using only the default trust store");
            return List.of();
        }

        log.info("loading additional certificates from bundle file at {}", additionalTrustedCertsFile);

        try {
            var cf = CertificateFactory.getInstance(CERTIFICATE_FACTORY_TYPE);
            return cf.generateCertificates(new FileInputStream(additionalTrustedCertsFile))
                    .stream()
                    .map(X509Certificate.class::cast)
                    .toList();
        } catch (CertificateException e) {
            // cannot happen because each JRE implementation is supposed to offer the used type
            throw new RuntimeException(e);
        } catch (FileNotFoundException e) {
            log.error("additional certificates bundle file at `{}` does not exist", additionalTrustedCertsFile);
            throw new RuntimeException("additional certificates bundle file at `%s` does not exist".formatted(
                    additionalTrustedCertsFile));
        }
    }

    private KeyStore loadKeyStore(File keyStoreFile, String keyStorePassword) throws IOException {
        if (!keyStoreFile.exists()) {
            throw new IOException("key store at `%s` does not exist".formatted(keyStoreFile.getAbsolutePath()));
        }

        try (var fis = new FileInputStream(keyStoreFile)) {
            var keyStore = getBlankKeyStore();
            keyStore.load(fis, keyStorePassword.toCharArray());

            return keyStore;
        } catch (CertificateException | NoSuchAlgorithmException e) {
            throw new RuntimeException(e);
        }
    }

    private KeyStore loadDefaultKeyStore() throws IOException {
        var javaHomeDir = System.getProperty(JAVA_HOME_PROPERTY);

        if (javaHomeDir == null || javaHomeDir.isEmpty()) {
            throw new RuntimeException("Java home directory could not be found");
        }

        var keyStorePath = Paths.get(javaHomeDir, "lib", "security", "cacerts");
        var keyStorePassword = getTrustStorePassword();

        return loadKeyStore(new File(keyStorePath.toString()), keyStorePassword);
    }

    private String getTrustStorePassword() {
        var trustStorePassword = System.getProperty(TRUST_STORE_PASSWORD_PROPERTY);
        if (trustStorePassword == null || trustStorePassword.isEmpty()) {
            return DEFAULT_TRUSTSTORE_PASSWORD; // default keystore password
        } else {
            return trustStorePassword;
        }
    }

    private KeyStore getBlankKeyStore() {
        try {
            return KeyStore.getInstance(KeyStore.getDefaultType());
        } catch (KeyStoreException e) {
            // cannot happen, since the used algorithm needs to be provided by EVERY JRE implementation
            throw new RuntimeException(e);
        }
    }
}
