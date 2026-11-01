package com.app.androidlab.sample.crypto;
import android.util.Log;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;

import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;

import javax.crypto.Cipher;
import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.security.*;
import java.security.spec.PKCS8EncodedKeySpec;
import java.security.spec.X509EncodedKeySpec;
import java.util.Base64;
import java.util.HashMap;
import java.util.Map;

public class JavaCryptoUtils {
    private static final String TAG = "JavaCryptoUtils";


    public static String getAllInfo(String input) {
        Map<String, Object> result = allMapInfo( input);

        Gson gson = new GsonBuilder().setPrettyPrinting().create();

        String resultStr = gson.toJson(result);

        Log.d(TAG, new GsonBuilder().create().toJson(result));

        return resultStr;
    }

    public static Map<String, Object> allMapInfo(String input) {
        Map<String, Object> result = new HashMap<>();
        String keyStr="1234567812345678";
        try {
//            result.put("encryptAES", encryptAES(input,keyStr));
//            result.put("decryptAES", decryptAES(encryptAES(input,keyStr),keyStr));
            result.put("md5", encryptMD5(input));
            result.put("sha1", encryptSha1(input));
            result.put("encryptHMAC", encryptHMAC(input,keyStr));
        } catch (Exception e) {
            Log.e(TAG, "allMapInfo: ", e);
        }
        return result;

    }

//    // AES Encryption
//    public static String encryptAES(String input, String keyStr) throws Exception {
//        SecretKeySpec key = new SecretKeySpec(keyStr.getBytes(), "AES");
//        Cipher cipher = Cipher.getInstance("AES");
//        cipher.init(Cipher.ENCRYPT_MODE, key);
//        byte[] encrypted = cipher.doFinal(input.getBytes());
//        return bytes2HexString(encrypted);}
//
//    // AES Decryption
//    public static String decryptAES(String input, String keyStr) throws Exception {
//        SecretKeySpec key = new SecretKeySpec(keyStr.getBytes(), "AES");
//        Cipher cipher = Cipher.getInstance("AES");
//        cipher.init(Cipher.DECRYPT_MODE, key);
//        byte[] decrypted = cipher.doFinal(input.getBytes());
//        return bytes2HexString(decrypted);
//    }

    // RSA Encryption
    public static String encryptRSA(String input, String base64PublicKey) throws Exception {
        byte[] decodedKey = Base64.getDecoder().decode(base64PublicKey);
        PublicKey key = KeyFactory.getInstance("RSA").generatePublic(new X509EncodedKeySpec(decodedKey));
        Cipher cipher = Cipher.getInstance("RSA");
        cipher.init(Cipher.ENCRYPT_MODE, key);
        byte[] encrypted = cipher.doFinal(input.getBytes());
        return bytes2HexString(encrypted);
    }

    // RSA Decryption - Assuming you have the private key in a similar Base64 encoded format
    public static String decryptRSA(String input, String base64PrivateKey) throws Exception {
        byte[] decodedKey = Base64.getDecoder().decode(base64PrivateKey);
        PrivateKey key = KeyFactory.getInstance("RSA").generatePrivate(new PKCS8EncodedKeySpec(decodedKey));
        Cipher cipher = Cipher.getInstance("RSA");
        cipher.init(Cipher.DECRYPT_MODE, key);
        byte[] decrypted = cipher.doFinal(Base64.getDecoder().decode(input));
        return bytes2HexString(decrypted);
    }

    // HMAC Calculation
    private static String encryptHMAC(String data, String key) throws Exception {
        SecretKeySpec secretKeySpec = new SecretKeySpec(key.getBytes(), "HmacSHA256");
        Mac mac = Mac.getInstance("HmacSHA256");
        mac.init(secretKeySpec);
        byte[] hmacData = mac.doFinal(data.getBytes());
        return bytes2HexString(hmacData);
    }



    private static String encryptSha1(String str) {
        try {
            // 获取SHA1算法实例
            MessageDigest md = MessageDigest.getInstance("SHA1");
            // 将字符串转换为字节数组，执行加密操作
            byte[] bytes = md.digest(str.getBytes());
            // 将字节数组转换为十六进制字符串
            return bytes2HexString(bytes);
        } catch (NoSuchAlgorithmException e) {
            e.printStackTrace();
            return null;
        }
    }



    /**
     * MD5加密字符串
     *
     * @param str 待加密的字符串
     * @return 加密后的字符串
     */
    private static String encryptMD5(String str) {
        try {
            // 获取MD5算法实例
            MessageDigest md = MessageDigest.getInstance("MD5");
            // 将字符串转换为字节数组，执行加密操作
            byte[] bytes = md.digest(str.getBytes());
            // 将字节数组转换为十六进制字符串
            return bytes2HexString(bytes);
        } catch (NoSuchAlgorithmException e) {
            e.printStackTrace();
            return null;
        }
    }



    /**
     * 将字节数组转换为十六进制字符串
     *
     * @param bytes 待转换的字节数组
     * @return 转换后的十六进制字符串
     */
    private static String bytes2HexString(byte[] bytes) {
        StringBuilder stringBuilder = new StringBuilder();
        for (byte b : bytes) {
            String hex = Integer.toHexString(b & 0xFF);
            if (hex.length() == 1) {
                stringBuilder.append("0");
            }
            stringBuilder.append(hex);
        }
        return stringBuilder.toString();
    }
}

