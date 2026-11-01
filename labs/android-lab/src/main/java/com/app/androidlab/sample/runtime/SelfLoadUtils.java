package com.app.androidlab.sample.runtime;

public class SelfLoadUtils {

    static {
        System.loadLibrary("elf_loader_module");

    }


    public static native String stringFromJNICpp();
}

