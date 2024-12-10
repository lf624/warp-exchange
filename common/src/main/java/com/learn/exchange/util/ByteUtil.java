package com.learn.exchange.util;

import java.util.HexFormat;

public class ByteUtil {

    // convert bytes to Hex String (all lower case)
    public static String toHexString(byte[] b) {
        return HexFormat.of().formatHex(b);
    }

    // Convert byte to hex string (all lower-case)
    public static String toHex(byte b) {
        return toHexString(new byte[] {b});
    }
}
