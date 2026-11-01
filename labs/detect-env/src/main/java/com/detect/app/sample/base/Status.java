package com.detect.app.sample.base;

// Status.java
public enum Status {
    NORMAL(0),
    ABNORMAL(1),
    UNKNOWN(2),

    TODOCheck(3),

    JAVACheck(4),

    NDKCHeck(5);

    private final int value;

    private Status(int value) {
        this.value = value;
    }

    public int getValue() {
        return value;
    }

    public static Status fromValue(int value) {
        for (Status status : Status.values()) {
            if (status.value == value) {
                return status;
            }
        }
        throw new IllegalArgumentException("Invalid value: " + value);
    }
}