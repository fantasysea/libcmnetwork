package com.chaomeng.libcmnetwork.Exception;

import java.net.SocketException;

public class TimeoutException extends SocketException {
    public TimeoutException(String s) {
        super(s);
    }

}
