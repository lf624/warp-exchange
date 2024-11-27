package com.learn.exchange.util;

import java.util.HexFormat;

public class ByteUtil {

    public static String toHexString(byte[] b) {
        return HexFormat.of().formatHex(b);
    }

    public static String toHex(byte b) {
        return toHexString(new byte[] {b});
    }
}
