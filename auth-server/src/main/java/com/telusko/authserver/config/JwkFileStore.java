package com.telusko.authserver.config;

import com.nimbusds.jose.jwk.RSAKey;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.NoSuchAlgorithmException;
import java.security.interfaces.RSAPrivateKey;
import java.security.interfaces.RSAPublicKey;
import java.text.ParseException;
import java.util.UUID;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

// Guarda en disco el par de claves RSA con que se firman los tokens.
// Sin esto habria una clave nueva en cada arranque y todos los tokens emitidos dejarian de validar.
final class JwkFileStore {

    private static final Logger log = LoggerFactory.getLogger(JwkFileStore.class);

    private JwkFileStore() {
    }

    static RSAKey loadOrCreate(Path path) {
        try {
            if (Files.exists(path)) {
                log.info("Clave de firma cargada desde {}", path.toAbsolutePath());
                return RSAKey.parse(Files.readString(path));
            }
            KeyPairGenerator generator = KeyPairGenerator.getInstance("RSA");
            generator.initialize(2048);
            KeyPair keyPair = generator.generateKeyPair();
            RSAKey key = new RSAKey.Builder((RSAPublicKey) keyPair.getPublic())
                    .privateKey((RSAPrivateKey) keyPair.getPrivate())
                    .keyID(UUID.randomUUID().toString())
                    .build();
            if (path.getParent() != null) {
                Files.createDirectories(path.getParent());
            }
            // Incluye la clave privada: el archivo no debe subirse a git ni salir del servidor
            Files.writeString(path, key.toJSONString());
            log.info("Clave de firma nueva generada en {}", path.toAbsolutePath());
            return key;
        } catch (IOException | ParseException | NoSuchAlgorithmException e) {
            throw new IllegalStateException("No se pudo cargar ni crear la clave de firma en " + path, e);
        }
    }
}
