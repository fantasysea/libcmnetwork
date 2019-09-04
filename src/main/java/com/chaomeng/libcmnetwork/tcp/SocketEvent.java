package com.chaomeng.libcmnetwork.tcp;

/**
 * socket自带的事件
 */
public  class SocketEvent {
    public static String PING = "ping";         //
    public static String PONG = "pong";         //
    public static String CLOSE = "close";       //关闭
    public static String CONNECT = "connect";   //连接成功
    public static String MESSAGE = "message";   //发送的事件
    public static String END = "end";           //end之后不能读取数据
    public static String ERROR = "error";       //错误
    public static String LOOKUP = "lookup";     //正在连
    public static String TIMEOUT = "timeout";  //正在超时

}