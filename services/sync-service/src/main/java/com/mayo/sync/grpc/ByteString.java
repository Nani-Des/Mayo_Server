package com.mayo.sync.grpc;

/**
 * Simplified ByteString implementation
 */
public class ByteString {
    private final byte[] bytes;

    private ByteString(byte[] bytes) {
        this.bytes = bytes != null ? bytes.clone() : new byte[0];
    }

    public byte[] toByteArray() {
        return bytes.clone();
    }

    public static ByteString copyFrom(byte[] bytes) {
        return new ByteString(bytes);
    }

    public static ByteString empty() {
        return new ByteString(new byte[0]);
    }
}