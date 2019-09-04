package com.chaomeng.libcmnetwork.tcp;

import com.chaomeng.libcmnetwork.MessageEvents;
import com.chaomeng.libcmnetwork.Url;
import com.chaomeng.libcmnetwork.utils.SLog;

import org.xml.sax.Parser;

import java.net.Socket;
import java.net.URI;
import java.net.URISyntaxException;
import java.net.URL;
import java.util.ArrayList;
import java.util.List;

/**
 * 创建socket链接
 */
public class SocketManager {

    private List<MySocket> sockets = new ArrayList<>();




    public static class SocketBuilder{
        private String mAddress;
        private int mPort;
        private Options mOptions;
        private MessageEvents mMessageEvents;
        private SLog mSLog;
        public SocketBuilder setmAddress(String mAddress) {
            this.mAddress = mAddress;
            return this;
        }

        public SocketBuilder setmOptions(Options mOptions) {
            this.mOptions = mOptions;
            return this;
        }

        public SocketBuilder setmPort(int mPort) {
            this.mPort = mPort;
            return this;
        }

        public SocketBuilder setMessageEvents(MessageEvents messageEvents) {
            this.mMessageEvents = messageEvents;
            return this;
        }

        public SocketBuilder setmSLog(SLog mSLog) {
            this.mSLog = mSLog;
            return this;
        }

        public MySocket build(){
            return new MySocket(mAddress,mPort,mOptions,mMessageEvents,mSLog);
        }
    }

    /**
     * 对接口和协议头尾做了一些定义
     */
    public static class Options {
        //心跳间隔
        public long heartbeatInterval = 5000;
        //心跳重试次数
        public int heartbeatRetryTimes = 3;
        //是否自动断开重连
        public boolean reconnection = true;
        //重连间隔
        public long reconnectionDelay = 5000;

        //连接超时时间
        public int connectTimeout = 5000;


        public int eventLegth = 2;//事件长度
        public int contentLegth = 2;//内容长度
        public int seqNumLegth = 3;//seqnum，循环0 -0xffff

        //等待返回超时时间
        public int requestWaitTimeout = 4000;
        //请求超时重试次数
        public int requestRetryTimes = 3;

        //是否输入日志
        public boolean debug = false;
    }
}
