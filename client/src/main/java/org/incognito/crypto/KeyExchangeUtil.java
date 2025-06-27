package org.incognito.crypto;

import org.json.JSONObject;

import javax.crypto.SecretKey;
import javax.crypto.spec.SecretKeySpec;
import java.security.*;
import java.security.spec.X509EncodedKeySpec;
import java.util.Base64;

public class KeyExchangeUtil {

    public static PublicKey decodePublicKey(String base64Key) throws Exception {
        byte[] decoded = Base64.getDecoder().decode(base64Key);
        X509EncodedKeySpec spec = new X509EncodedKeySpec(decoded);
        return KeyFactory.getInstance("RSA").generatePublic(spec);
    }

    public static String getSessionIdFromQRCode(String qrContent) {
        return new JSONObject(qrContent).getString("sessionId");
    }

    public static String getPublicKeyFromQRCode(String qrContent) {
        return new JSONObject(qrContent).getString("publicKey");
    }

    public static SecretKey decodeAESKey(byte[] keyBytes) {
        return new SecretKeySpec(keyBytes, 0, keyBytes.length, "AES");
    }
}
