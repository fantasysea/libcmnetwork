package com.chaomeng.libcmnetwork;

import java.util.HashMap;
import java.util.Map;

/**
 * 消息类型转换，需要使用者实现
 */
public interface MessageEvents {

    String getEvent(int values);

    int getValue(String event);

    int getPing();

    int getPong();

}
