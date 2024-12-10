package com.learn.exchange.util;

import javax.crypto.Mac;
import javax.crypto.SecretKey;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;

// Util for hashing
public class HashUtil {

    public static String sha256(byte[] input) {
        MessageDigest md = null;
        try {
            md = MessageDigest.getInstance("SHA-256");
        }catch (NoSuchAlgorithmException e) {
            throw new RuntimeException(e);
        }
        md.update(input);
        return ByteUtil.toHexString(md.digest());
    }

    // Generate SHA-256 as hex string (all lower-case)
    public static String sha256(String input) {
        return sha256(input.getBytes(StandardCharsets.UTF_8));
    }

    // Do HMAC-SHA256
    public static byte[] hmacSha256AsBytes(byte[] data, byte[] key) {
        SecretKey skey = new SecretKeySpec(key, "HmacSHA256");
        Mac mac;
        try {
            mac = Mac.getInstance("HmacSHA256");
            mac.init(skey);
        }catch (GeneralSecurityException e) {
            throw new RuntimeException(e);
        }
        mac.update(data);
        return mac.doFinal();
    }

    public static String hmacSha256(byte[] data, byte[] key) {
        return ByteUtil.toHexString(hmacSha256AsBytes(data, key));
    }

    public static String hmacSha256(String data, String key) {
        return hmacSha256(data.getBytes(StandardCharsets.UTF_8), key.getBytes(StandardCharsets.UTF_8));
    }
}
