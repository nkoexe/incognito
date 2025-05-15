package org.incognito.crypto;

import javax.crypto.*;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.SecretKeySpec;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.security.*;
import java.security.spec.X509EncodedKeySpec;
import java.util.Base64;

public class CryptoManager {
    private final KeyPair rsaKeyPair;
    private SecretKey aesSessionKey;

    public CryptoManager() throws Exception {
        KeyPairGenerator gen = KeyPairGenerator.getInstance("RSA");
        gen.initialize(2048);
        this.rsaKeyPair = gen.generateKeyPair();
    }

    public String getPublicKeyBase64() {
        return Base64.getEncoder().encodeToString(rsaKeyPair.getPublic().getEncoded());
    }

    public void setAesSessionKey(SecretKey key) {
        this.aesSessionKey = key;
    }

    public PublicKey decodePublicKey(String base64) throws Exception {
        byte[] decoded = Base64.getDecoder().decode(base64);
        X509EncodedKeySpec spec = new X509EncodedKeySpec(decoded);
        return KeyFactory.getInstance("RSA").generatePublic(spec);
    }

    public SecretKey generateAESKey() throws Exception {
        KeyGenerator gen = KeyGenerator.getInstance("AES");
        gen.init(256);
        return gen.generateKey();
    }

    public byte[] encryptAES(String message) throws Exception {
        byte[] iv = new byte[12];
        new SecureRandom().nextBytes(iv);

        Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
        cipher.init(Cipher.ENCRYPT_MODE, aesSessionKey, new GCMParameterSpec(128, iv));
        byte[] encrypted = cipher.doFinal(message.getBytes(StandardCharsets.UTF_8));

        ByteBuffer buffer = ByteBuffer.allocate(iv.length + encrypted.length);
        buffer.put(iv);
        buffer.put(encrypted);
        return buffer.array();
    }

    public String decryptAES(byte[] data) throws Exception {
        ByteBuffer buffer = ByteBuffer.wrap(data);
        byte[] iv = new byte[12];
        buffer.get(iv);
        byte[] cipherText = new byte[buffer.remaining()];
        buffer.get(cipherText);

        Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
        cipher.init(Cipher.DECRYPT_MODE, aesSessionKey, new GCMParameterSpec(128, iv));
        byte[] plain = cipher.doFinal(cipherText);
        return new String(plain, StandardCharsets.UTF_8);
    }

    public byte[] encryptAESKey(PublicKey peerPublicKey, SecretKey aesKey) throws Exception {
        Cipher cipher = Cipher.getInstance("RSA");
        cipher.init(Cipher.ENCRYPT_MODE, peerPublicKey);
        return cipher.doFinal(aesKey.getEncoded());
    }

    public SecretKey decryptAESKey(byte[] encryptedAesKey) throws Exception {
        Cipher cipher = Cipher.getInstance("RSA");
        cipher.init(Cipher.DECRYPT_MODE, rsaKeyPair.getPrivate());
        byte[] decoded = cipher.doFinal(encryptedAesKey);
        return new SecretKeySpec(decoded, "AES");
    }
}
