package org.incognito.crypto;

import javax.crypto.*;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.SecretKeySpec;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.security.*;
import java.security.spec.X509EncodedKeySpec;
import java.util.Base64;
import java.util.logging.Logger;

public class CryptoManager {
    private final KeyPair rsaKeyPair;
    private SecretKey aesSessionKey;
    private PublicKey otherUserPublicKey;

    public CryptoManager() throws Exception {
        KeyPairGenerator gen = KeyPairGenerator.getInstance("RSA");
        gen.initialize(2048);
        this.rsaKeyPair = gen.generateKeyPair();
    }

    public String getPublicKeyBase64() {
        return Base64.getEncoder().encodeToString(rsaKeyPair.getPublic().getEncoded());
    }

    public String getOtherUserPublicKeyBase64() {
        if (otherUserPublicKey == null) {
            throw new IllegalStateException("Other user's public key has not been set.");
        }
        return Base64.getEncoder().encodeToString(otherUserPublicKey.getEncoded());
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

    public void setOtherUserPublicKey(String base64PublicKey) throws Exception {
        byte[] decoded = Base64.getDecoder().decode(base64PublicKey);
        X509EncodedKeySpec keySpec = new X509EncodedKeySpec(decoded);
        KeyFactory keyFactory = KeyFactory.getInstance("RSA"); // or your algorithm
        otherUserPublicKey = keyFactory.generatePublic(keySpec);
    }

    public PublicKey getOtherUserPublicKey() {
        return otherUserPublicKey;
    }

    public String encryptWithOtherUserPublicKey(String data) throws Exception {
        if (otherUserPublicKey == null) {
            throw new IllegalStateException("Other user's public key has not been set.");
        }

        Cipher cipher = Cipher.getInstance("RSA/ECB/PKCS1Padding");
        cipher.init(Cipher.ENCRYPT_MODE, otherUserPublicKey);

        byte[] encryptedBytes = cipher.doFinal(data.getBytes(StandardCharsets.UTF_8));
        return Base64.getEncoder().encodeToString(encryptedBytes);
    }

    public String decryptWithPrivateKey(String encryptedData) throws Exception {
        Cipher cipher = Cipher.getInstance("RSA/ECB/PKCS1Padding");
        cipher.init(Cipher.DECRYPT_MODE, rsaKeyPair.getPrivate());

        byte[] encryptedBytes = Base64.getDecoder().decode(encryptedData);
        byte[] decryptedBytes = cipher.doFinal(encryptedBytes);

        return new String(decryptedBytes, StandardCharsets.UTF_8);
    }

    public SecretKey getAesSessionKey() {
        if (aesSessionKey == null) {
            throw new IllegalStateException("AES session key has not been set.");
        }
        return aesSessionKey;
    }

    public String encrypt(String plainText) throws Exception {
        if (aesSessionKey == null) {
            throw new IllegalStateException("AES session key has not been set.");
        }
        byte[] encrypted = encryptAES(plainText);
        return Base64.getEncoder().encodeToString(encrypted);
    }

    public String decrypt(String encryptedText) throws Exception {
        if (aesSessionKey == null) {
            throw new IllegalStateException("AES session key has not been set.");
        }
        byte[] decoded = Base64.getDecoder().decode(encryptedText);
        return decryptAES(decoded);
    }

    public String encryptSessionKeyForPeer() throws Exception {
        if (otherUserPublicKey == null) {
            throw new IllegalStateException("Other user's public key not set");
        }
        if (aesSessionKey == null) {
            throw new IllegalStateException("Session key not generated");
        }

        byte[] keyBytes = aesSessionKey.getEncoded();
        String keyBase64 = Base64.getEncoder().encodeToString(keyBytes);
        return encryptWithOtherUserPublicKey(keyBase64);
    }

    public boolean setSessionKeyFromEncrypted(String encryptedKey) {
        try {
            String decryptedKeyBase64 = decryptWithPrivateKey(encryptedKey);
            byte[] keyBytes = Base64.getDecoder().decode(decryptedKeyBase64);
            this.aesSessionKey = new SecretKeySpec(keyBytes, "AES");
            return true;
        } catch (Exception e) {
            Logger.getLogger(CryptoManager.class.getName()).severe("Failed to set session key: " + e.getMessage());
            return false;
        }
    }
}
