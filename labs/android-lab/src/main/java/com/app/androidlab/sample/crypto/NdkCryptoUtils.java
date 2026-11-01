package com.app.androidlab.sample.crypto;

public class NdkCryptoUtils {
    static {
        System.loadLibrary("crypto_module");

    }




    public static native String md5FromNative(String inputStr);

}
