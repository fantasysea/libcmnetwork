package com.chaomeng.libcmnetwork.utils;

public class ByteUtils {
    /**
     * 合并多个字节
     * @param values
     * @return
     */
    public static byte[] byteMergerAll(byte[]... values) {
        int length_byte = 0;
        for (int i = 0; i < values.length; i++) {
            length_byte += values[i].length;
        }
        byte[] all_byte = new byte[length_byte];
        int countLength = 0;
        for (int i = 0; i < values.length; i++) {
            byte[] b = values[i];
            System.arraycopy(b, 0, all_byte, countLength, b.length);
            countLength += b.length;
        }
        return all_byte;
    }

    /**
     * 将16进制字符串转换为byte[]
     * 大端模式
     *
     * @param str
     * @return
     */
    public static byte[] toBigEndianBytes(String str) {
        if (str == null || str.trim().equals("")) {
            return new byte[0];
        }

        byte[] bytes = new byte[str.length() / 2];
        for (int i = 0; i < str.length() / 2; i++) {
            String subStr = str.substring(i * 2, i * 2 + 2);
            bytes[i] = (byte) Integer.parseInt(subStr, 16);
        }
        return bytes;
    }


    /**
     *
     * @param bytes 16进制字符串转long
     * @return
     */
    public static long bytes2Long(byte[] bytes) {
        return Long.parseLong(bytesToHexFun3(bytes), 16);
    }

    /**
     *
     * @param bytes 16进制字符串转int
     * @return
     */
    public static int bytes2int(byte[] bytes) {
        return Integer.parseInt(bytesToHexFun3(bytes), 16);
    }

    public static String bytesToHexFun3(byte[] bytes) {
        StringBuilder buf = new StringBuilder(bytes.length * 2);
        for (byte b : bytes) { // 使用String的format方法进行转换
            buf.append(String.format("%02x", new Integer(b & 0xff)));
        }

        return buf.toString();
    }
}
