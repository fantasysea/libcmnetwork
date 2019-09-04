package com.chaomeng.libcmnetwork.protocol;

import com.chaomeng.libcmnetwork.utils.ByteUtils;

/**
 * 发送的数据包结构
 * event：各种事件，如ping pong message
 * 结构：message:event-length-seqNum-content
 * 但是ping pong仅仅有事件就可以了，不需要内容和seqnum
 *  ping:0000
 *  pong:0001
 *
 */
public class Packet {
    private byte[] event;//2字节
    private byte[] length = new byte[0];//2字节 ping pong 的时候没有
    private byte[] seqNum = new byte[0];//3字节 ping pong 的时候没有
    private byte[] content = new byte[0]; // 动态长度 ping pong 的时候没有


    public byte[] getEvent() {
        return event;
    }

    public byte[] getSeqNum() {
        return seqNum;
    }

    public void setSeqNum(byte[] seqNum) {
        this.seqNum = seqNum;
    }

    public void setEvent(byte[] event) {
        this.event = event;
    }

    public byte[] getContent() {
        return content;
    }

    public void setContent(byte[] content) {
        this.content = content;
    }


    public byte[] getLength() {
        return length;
    }

    public void setLength(byte[] length) {
        this.length = length;
    }

    public byte[] getByte(){

        return ByteUtils.byteMergerAll(event,length,seqNum,content);
    }
}
