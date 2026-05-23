package org.chrisbarbati.pushoverclient.crypto;

import org.junit.jupiter.api.Test;

import javax.crypto.Cipher;
import javax.crypto.Mac;
import javax.crypto.spec.IvParameterSpec;
import javax.crypto.spec.SecretKeySpec;
import java.io.ByteArrayInputStream;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.Base64;
import java.util.HexFormat;
import java.util.zip.GZIPInputStream;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * Unit tests validating E2EE payload compatibility with the documented algorithm.
 *
 * @since 0.1.0
 * @author Christian Barbati
 */
class E2eeEncryptorTest {

    /**
     * Verifies encrypted output can be decrypted using the same algorithm steps.
     *
     * @throws Exception when cryptography primitives fail unexpectedly
     * @since 0.1.0
     */
    @Test
    void encryptField_roundTripsWithSpecAlgorithm() throws Exception {
        String keyHex = "0123456789abcdef0123456789abcdef0123456789abcdef0123456789abcdef";
        String plaintext = "message with unicode-ish ascii fallback text";

        E2eeEncryptor encryptor = new E2eeEncryptor();
        String encrypted = encryptor.encryptField(plaintext, keyHex);

        String decrypted = decrypt(encrypted, keyHex);
        assertEquals(plaintext, decrypted);
    }

    /**
     * Decrypts a payload produced by {@link E2eeEncryptor#encryptField(String, String)}.
     *
     * @param encryptedBase64 encrypted payload in Base64
     * @param keyHex 64-character hex key
     * @return decrypted plaintext
     * @throws Exception when decryption or decompression fails
     * @since 0.1.0
     */
    private String decrypt(String encryptedBase64, String keyHex) throws Exception {
        byte[] payload = Base64.getDecoder().decode(encryptedBase64);
        byte[] key = HexFormat.of().parseHex(keyHex);

        byte[] iv = Arrays.copyOfRange(payload, 0, 16);
        byte[] hmac = Arrays.copyOfRange(payload, payload.length - 32, payload.length);
        byte[] ciphertext = Arrays.copyOfRange(payload, 16, payload.length - 32);

        Mac mac = Mac.getInstance("HmacSHA256");
        mac.init(new SecretKeySpec(key, "HmacSHA256"));
        byte[] expected = mac.doFinal(concat(iv, ciphertext));
        assertEquals(HexFormat.of().formatHex(expected), HexFormat.of().formatHex(hmac));

        Cipher cipher = Cipher.getInstance("AES/CBC/PKCS5Padding");
        cipher.init(Cipher.DECRYPT_MODE, new SecretKeySpec(key, "AES"), new IvParameterSpec(iv));
        byte[] compressed = cipher.doFinal(ciphertext);

        try (GZIPInputStream gzipInputStream = new GZIPInputStream(new ByteArrayInputStream(compressed))) {
            return new String(gzipInputStream.readAllBytes(), StandardCharsets.UTF_8);
        }
    }

    /**
     * Concatenates two byte arrays for test-side cryptographic validation.
     *
     * @param left left bytes
     * @param right right bytes
     * @return concatenated bytes
     * @since 0.1.0
     */
    private static byte[] concat(byte[] left, byte[] right) {
        byte[] result = new byte[left.length + right.length];
        System.arraycopy(left, 0, result, 0, left.length);
        System.arraycopy(right, 0, result, left.length, right.length);
        return result;
    }
}


