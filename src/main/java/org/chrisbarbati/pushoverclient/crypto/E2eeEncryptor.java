package org.chrisbarbati.pushoverclient.crypto;

import org.chrisbarbati.pushoverclient.exception.FieldEncryptionException;
import org.chrisbarbati.pushoverclient.exception.InvalidEncryptionKeyException;
import org.chrisbarbati.pushoverclient.exception.PushoverClientException;

import javax.crypto.Cipher;
import javax.crypto.Mac;
import javax.crypto.spec.IvParameterSpec;
import javax.crypto.spec.SecretKeySpec;
import java.io.ByteArrayOutputStream;
import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.security.SecureRandom;
import java.util.Base64;
import java.util.HexFormat;
import java.util.Objects;
import java.util.zip.GZIPOutputStream;

/**
 * Implements Pushover's end-to-end field encryption scheme.
 *
 * @implNote The output format is IV + ciphertext + HMAC, Base64 encoded.
 * @since 0.1.0
 * @author Christian Barbati
 */
public class E2eeEncryptor {
    private static final SecureRandom SECURE_RANDOM = new SecureRandom();

    /**
     * Encrypts one API field using the documented Pushover E2EE format:
     * gzip + AES-256-CBC + HMAC-SHA256, then Base64.
     *
     * @param plaintext field value to encrypt
     * @param hexKey 64-character hex key
     * @return Base64 encoded payload in IV + ciphertext + HMAC layout
     * @since 0.1.0
     * @author Christian Barbati
     */
    public String encryptField(String plaintext, String hexKey) {
        Objects.requireNonNull(plaintext, "plaintext is required");
        Objects.requireNonNull(hexKey, "hexKey is required");

        // First enforce that the key is valid before doing any work on the plaintext.
        byte[] key = decodeHexKey(hexKey);

        // Next generate a random IV
        byte[] iv = new byte[16];
        SECURE_RANDOM.nextBytes(iv);

        // Each field is compressed before encryption per Pushover's spec.
        byte[] compressed = gzip(plaintext);

        byte[] ciphertext = aes256CbcEncrypt(compressed, key, iv);

        // Integrity covers IV + ciphertext as required by the API.
        byte[] hmac = hmacSha256(concat(iv, ciphertext), key);

        byte[] output = concat(concat(iv, ciphertext), hmac);

        return Base64.getEncoder().encodeToString(output);
    }

    /**
     * Parses and validates the configured hex key.
     *
     * @param hexKey 64-character hex key
     * @return raw 32-byte key
     * @since 0.1.0
     * @author Christian Barbati
     */
    private static byte[] decodeHexKey(String hexKey) {
        if (hexKey.length() != 64) {
            throw new InvalidEncryptionKeyException("Encryption key must be a 64-character hex string");
        }
        try {
            return HexFormat.of().parseHex(hexKey);
        } catch (IllegalArgumentException ex) {
            throw new InvalidEncryptionKeyException("Encryption key is not valid hex", ex);
        }
    }

    /**
     * Gzip-compresses plaintext bytes in UTF-8.
     *
     * @param plaintext uncompressed field value
     * @return compressed payload bytes
     * @since 0.1.0
     * @author Christian Barbati
     */
    private static byte[] gzip(String plaintext) {
        try {
            ByteArrayOutputStream outputStream = new ByteArrayOutputStream();
            try (GZIPOutputStream gzipOutputStream = new GZIPOutputStream(outputStream)) {
                gzipOutputStream.write(plaintext.getBytes(StandardCharsets.UTF_8));
            }
            return outputStream.toByteArray();
        } catch (Exception ex) {
            throw new PushoverClientException("Failed to gzip plaintext", ex);
        }
    }

    /**
     * Encrypts compressed bytes with AES/CBC/PKCS5Padding (PKCS7-compatible in JCE).
     *
     * @param compressed compressed plaintext
     * @param key 256-bit AES key
     * @param iv random 16-byte IV
     * @return ciphertext bytes
     * @since 0.1.0
     * @author Christian Barbati
     */
    private static byte[] aes256CbcEncrypt(byte[] compressed, byte[] key, byte[] iv) {
        try {
            Cipher cipher = Cipher.getInstance("AES/CBC/PKCS5Padding");
            cipher.init(Cipher.ENCRYPT_MODE, new SecretKeySpec(key, "AES"), new IvParameterSpec(iv));
            return cipher.doFinal(compressed);
        } catch (GeneralSecurityException ex) {
            throw new FieldEncryptionException("Failed to encrypt field", ex);
        }
    }

    /**
     * Computes HMAC-SHA256 bytes for the supplied payload.
     *
     * @param payload IV + ciphertext bytes
     * @param key shared secret key
     * @return hmac bytes
     * @since 0.1.0
     * @author Christian Barbati
     */
    private static byte[] hmacSha256(byte[] payload, byte[] key) {
        try {
            Mac mac = Mac.getInstance("HmacSHA256");
            mac.init(new SecretKeySpec(key, "HmacSHA256"));
            return mac.doFinal(payload);
        } catch (GeneralSecurityException ex) {
            throw new PushoverClientException("Failed to compute HMAC", ex);
        }
    }

    /**
     * Concatenates two byte arrays.
     *
     * @param left first array
     * @param right second array
     * @return combined array
     * @since 0.1.0
     * @author Christian Barbati
     */
    private static byte[] concat(byte[] left, byte[] right) {
        byte[] result = new byte[left.length + right.length];
        System.arraycopy(left, 0, result, 0, left.length);
        System.arraycopy(right, 0, result, left.length, right.length);
        return result;
    }
}

