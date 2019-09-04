package com.chaomeng.libcmnetwork.Exception;

import java.net.SocketException;

public class RequestException extends SocketException {
    public RequestException(String s) {
        super(s);
    }

}
