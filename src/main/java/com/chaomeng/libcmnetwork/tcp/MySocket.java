package com.chaomeng.libcmnetwork.tcp;

import android.util.Log;

import com.Emitter;
import com.chaomeng.libcmnetwork.Exception.RequestException;
import com.chaomeng.libcmnetwork.Exception.TimeoutException;
import com.chaomeng.libcmnetwork.MessageEvents;
import com.chaomeng.libcmnetwork.protocol.Packet;
import com.chaomeng.libcmnetwork.thread.EventThread;
import com.chaomeng.libcmnetwork.utils.ByteUtils;
import com.chaomeng.libcmnetwork.utils.SLog;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.net.Socket;
import java.net.SocketException;
import java.net.SocketTimeoutException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Queue;
import java.util.concurrent.TimeUnit;

import io.reactivex.Observable;
import io.reactivex.ObservableEmitter;
import io.reactivex.ObservableOnSubscribe;

/**
 * 自定义socket，实现了数据的分包，缓存
 */
public class MySocket extends Emitter {
    private String TAG = "MySocket";
    private String mAddress;
    private int mPort;
    private Socket mSocket;
    private InputStream mInputStream;
    private OutputStream mOutputStream;
    private volatile boolean connected;
    private boolean reconnecting;
    private boolean sending;
    private List<Packet> packetBuffer = new ArrayList<Packet>();
    private final Queue<byte[]> receiveBuffer = new LinkedList<byte[]>();
    private final Queue<Packet> sendBuffer = new LinkedList<Packet>();
    private SocketManager.Options mOptions;
    private MessageEvents mMessageEvents;
    private SLog mSLog;
    private int mSeqnum = 0;
    private int mNoReplyCount = 0;
    private int contentLegth;
    private volatile byte[] packetData = new byte[0];

    protected static Map<String, Integer> events = new HashMap<String, Integer>() {{
        put(SocketEvent.CONNECT, 1);
        put(SocketEvent.ERROR, 1);
        put(SocketEvent.TIMEOUT, 1);
        put(SocketEvent.CLOSE, 1);
        put(SocketEvent.LOOKUP, 1);
        put(SocketEvent.END, 1);
    }};


    public MySocket(String address, int port, SocketManager.Options options,MessageEvents messageEvents,SLog sLog) {
        this.mAddress = address;
        this.mPort = port;
        this.mOptions = options;
        this.mMessageEvents = messageEvents;
        this.mSLog = sLog;
    }

    /**
     * Connects the socket.
     */
    public MySocket connect() {
        EventThread.exec(new Runnable() {
            @Override
            public void run() {
                if (MySocket.this.connected) return;
                try{
                    mSocket = new Socket();
                    mSocket.connect(new InetSocketAddress(mAddress, mPort),mOptions.connectTimeout);
                    mSocket.setTcpNoDelay(true);
                    mSocket.setKeepAlive(true);
                    connected = true;
                    mInputStream = mSocket.getInputStream();
                    mOutputStream = mSocket.getOutputStream();
                    mNoReplyCount = 0;
                    emit(SocketEvent.CONNECT);
                    startRead();
                    startHeartbeat();
                }catch (Exception e){
                    e.printStackTrace();
                    emit(SocketEvent.ERROR,e);
                    onError(e);
                }
            }
        });
        return this;
    }

    /**
     * 由于网络问题出现错误,这个时候重连服务器
     * @param e
     */
    private void onError(Exception e) {
        if (e instanceof SocketException||e instanceof SocketTimeoutException){
            close(e.getMessage());
        }

        if (mOptions.debug){
            mSLog.d(TAG,"new error = "+ e.getMessage());
        }
    }


    /**
     * Connects the socket.
     */
    public void reconnect() {
        EventThread.exec(new Runnable() {
            @Override
            public void run() {
                if (MySocket.this.reconnecting) return;
                try{
                    reconnecting = true;
                    Observable.timer(mOptions.reconnectionDelay,TimeUnit.MILLISECONDS).subscribe((d)->{
                        try {
                            reconnecting = false;
                            //连接超时间
                            mSocket = new Socket();
                            mSocket.connect(new InetSocketAddress(mAddress, mPort),mOptions.connectTimeout);
                            mSocket.setTcpNoDelay(true);
                            connected = true;
                            startHeartbeat();
                            mInputStream = mSocket.getInputStream();
                            mOutputStream = mSocket.getOutputStream();
                            mNoReplyCount = 0;
                            startRead();
                            onconnect();
                        } catch (IOException e) {
                            e.printStackTrace();
                            emit(SocketEvent.ERROR,e);
                            onError(e);
                        }
                    });


                }catch (Exception e){
                    e.printStackTrace();
                    emit(SocketEvent.ERROR,e);
                    onError(e);
                }
            }
        });
    }


    /**
     * 心跳包
     */
    private void startHeartbeat() {
        Observable.interval(mOptions.heartbeatInterval, TimeUnit.MILLISECONDS).takeWhile((w)->{return connected;
        }).subscribe((aLong)->{
            mNoReplyCount++;
            if (mNoReplyCount>mOptions.heartbeatRetryTimes){
                mNoReplyCount = 0;
                Exception e = new TimeoutException("ping no reply 3 times");
                emit(SocketEvent.ERROR,e);
                onError(e);
            }
            MySocket.this.send(SocketEvent.PING);
        });
    }


    /**
     * 发一条数据到服务器
     * @param args 发送的数据，只能是二进制数据
     * @return
     */
    public synchronized int  send(final String event, final Object... args) {
        if (!event.equalsIgnoreCase(SocketEvent.PING)&&!event.equalsIgnoreCase(SocketEvent.PONG)){
            mSeqnum++;
            //最大3个字节
            if (mSeqnum> (int)Math.pow(2,mOptions.seqNumLegth*8)){
                mSeqnum = 0;
            }
        }
        emit(event,false,args);
        return mSeqnum;
    };

    /**
     * Emits an event. 发射一个事件
     *
     * @param event an event name.
     * @param args data to send.
     * @return a reference to this object.
     */
    @Override
    public Emitter emit(final String event, final Object... args) {
        return emit(event,true,args);
    }

    /**
     * Emits an event. 发射一个事件
     *
     * @param event an event name.
     * @param isinner true 表示内部事件，不发送到服务器
     * @param args data to send.
     * @return a reference to this object.
     */

    public Emitter emit(final String event, boolean isinner , final Object... args) {
        EventThread.exec(new Runnable() {
            @Override
            public void run() {
                //如果是给app自己的几个事件就内部消化
                if (events.containsKey(event)||isinner) {
                    MySocket.super.emit(event, args);
                    return;
                }

                //只有ping pong message register等才真的发送给用户
                Packet packet = new Packet();
                packet.setEvent(ByteUtils.toBigEndianBytes(String.format("%04x", mMessageEvents.getValue(event))));
                if (!event.equalsIgnoreCase(SocketEvent.PING)&&!event.equalsIgnoreCase(SocketEvent.PONG)){
                    if (mOptions.debug){
                        mSLog.d(TAG,"发射一个事件 event = "+event+"content = "+ByteUtils.bytesToHexFun3((byte[])args[0]));
                    }
                    packet.setSeqNum(ByteUtils.toBigEndianBytes(String.format("%06x", mSeqnum)));
                    packet.setContent((byte[])args[0]);
                    packet.setLength(ByteUtils.toBigEndianBytes(String.format("%04x", packet.getContent().length)));
                }
                if (MySocket.this.connected) {
                    //放在发送消息队列
                    MySocket.this.packet(packet);
                    waitFeedBack(packet);
                } else {
                    //放在离线送消息队列
                    MySocket.this.sendBuffer.add(packet);
                }
            }
        });
        return this;
    }

    //把读到的数据写入缓存
    public byte[] mergebyte(byte[] a, byte[] b, int begin, int end) {
        byte[] add = new byte[a.length + end - begin];
        int i = 0;
        for (i = 0; i < a.length; i++) {
            add[i] = a[i];
        }
        for (int k = begin; k < end; k++, i++) {
            add[i] = b[k];
        }
        return add;
    }


    /**
     *
     * 开始读取
     *
     */
    private void startRead(){
        new Thread(new Runnable() {
            @Override
            public void run() {

                while (connected){
                    try {
                        if (mInputStream == null) {
                            return;
                        }
                        //读取event，如果还没达到event的长度就继续读取，直到获取了为止
                        if (packetData.length < mOptions.eventLegth) {
                            //temp是应该继续读取的字节大小
                            byte[] temp = new byte[mOptions.eventLegth - packetData.length];
                            int couter = mInputStream.read(temp);
                            if (couter < 0) {
                                continue;
                            }
                            packetData = mergebyte(packetData, temp, 0, couter);

                            ////temp是应该继续读取的字节大小，couter是实际读到到的数据，如果没到temp我们期望的大小就继续读取
                            if (couter < temp.length) {
                                continue;
                            }
                        }
                        //转换event事件
                        byte[] eventLegthData = new byte[mOptions.eventLegth];
                        System.arraycopy(packetData,0,eventLegthData,0,mOptions.eventLegth);
                        String event = mMessageEvents.getEvent(ByteUtils.bytes2int( eventLegthData));
                        //如果返回的是没定义的事件，可以认为是恶意数据，那么直接断开重连
                        if (event==null){
                            Exception e = new TimeoutException("event not define，will close soon");
                            emit(SocketEvent.ERROR,e);
                            onError(e);
                            continue;
                        }
                        //ping pong直接处理
                        if (event.equals(SocketEvent.PING)||event.equals(SocketEvent.PONG)){
                            //完整的一个包，开始处理
                            handlepacket(packetData);
                            packetData = new byte[0];
                            continue;
                        }

                        if (packetData.length < (mOptions.eventLegth+mOptions.contentLegth)) {
                            byte[] temp = new byte[(mOptions.eventLegth+mOptions.contentLegth) - packetData.length];
                            int couter = mInputStream.read(temp);
                            if (couter < 0) {
                                continue;
                            }
                            packetData = mergebyte(packetData, temp, 0, couter);
                            if (couter < temp.length) {
                                continue;
                            }
                        }
                        //转换contentLegthD大小
                        byte[] contentLegthData = new byte[mOptions.contentLegth];
                        System.arraycopy(packetData,mOptions.eventLegth,contentLegthData,0,mOptions.contentLegth);
                        contentLegth  = ByteUtils.bytes2int(contentLegthData);
                        if (packetData.length < (mOptions.eventLegth+mOptions.contentLegth+mOptions.seqNumLegth)) {
                            byte[] temp = new byte[(mOptions.eventLegth+mOptions.contentLegth+mOptions.seqNumLegth) - packetData.length];
                            int couter = mInputStream.read(temp);
                            if (couter < 0) {
                                continue;
                            }
                            packetData = mergebyte(packetData, temp, 0, couter);
                            if (couter < temp.length) {
                                continue;
                            }
                        }

                        if (packetData.length < (mOptions.eventLegth+mOptions.contentLegth+mOptions.seqNumLegth+contentLegth)) {
                            byte[] temp = new byte[mOptions.eventLegth+mOptions.contentLegth+mOptions.seqNumLegth+contentLegth - packetData.length];
                            int couter = mInputStream.read(temp);

                            if (couter < 0) {
                                continue;
                            }
                            packetData = mergebyte(packetData, temp, 0, couter);
                            if (couter < temp.length) {
                                continue;
                            }
                        }
                        //完整的一个包，开始处理
                        handlepacket(packetData);
                        packetData = new byte[0];
                    } catch (IOException e) {
                        emit(SocketEvent.ERROR,e);
                        onError(e);
                        e.printStackTrace();
                    }
                }

            }
        }).start();
    }

    /**
     * 可以优化一下，之前的的读取已经拿到了是什么类型的了
     * @param data 一个完整的包
     */
    private void handlepacket(byte[] data) {
        EventThread.exec(new Runnable() {
            @Override
            public void run() {

                byte[] eventData = new byte[mOptions.eventLegth];
                System.arraycopy(data,0,eventData,0,eventData.length);
                String event = mMessageEvents.getEvent(ByteUtils.bytes2int( eventData));
                //有找到对应的事件
                if (event!=null){
                    if (event.equals(SocketEvent.PING)||event.equals(SocketEvent.PONG)){
                        if (event.equals(SocketEvent.PING)){
                            MySocket.this.send(SocketEvent.PONG,false);
                        }else if(event.equals(SocketEvent.PONG)){
                            mNoReplyCount=0;
                        }
                    }else {
                        byte[] contentLegthData = new byte[mOptions.contentLegth];
                        System.arraycopy(data,eventData.length,contentLegthData,0,contentLegthData.length);
                        byte[] seqNumData = new byte[mOptions.seqNumLegth];
                        System.arraycopy(data,eventData.length+contentLegthData.length,seqNumData,0,seqNumData.length);
                        int seqNum = ByteUtils.bytes2int(seqNumData);
                        myEmitter.onNext(seqNumData);

                        byte[] content = new byte[ByteUtils.bytes2int( contentLegthData)];
                        System.arraycopy(data,eventData.length+contentLegthData.length+seqNumData.length,content,0,content.length);
                        if (mOptions.debug){
                            mSLog.d(TAG,"new message event =  "+event+" seqNum = "+seqNum+" contentleght = "+ByteUtils.bytes2int( contentLegthData)+" content = "+ByteUtils.bytesToHexFun3(content));
                        }
                        Packet packet = new Packet();
                        packet.setEvent(eventData);
                        packet.setLength(contentLegthData);
                        packet.setSeqNum(seqNumData);
                        packet.setContent(content);
                        MySocket.this.emit(event,packet);
                    }

                }else {
                    if (mOptions.debug){
                        mSLog.d(TAG,"数据类型不认识,将清空所有缓存数据 = "+ByteUtils.bytesToHexFun3(data));
                    }
                }
            }
        });

    }


    public boolean isConnected(){
        return MySocket.this.connected;
    }
    /**
     *  重连完成
     */
    private void onconnect() {
        this.connected = true;
        this.emit(SocketEvent.CONNECT);
        this.emitBuffered();
    }

    /**
     *  重连接之后把之前的数据再次发出去 2
     */
    private void emitBuffered() {
        byte[] data;
        while ((data = this.receiveBuffer.poll()) != null) {
            this.handlepacket(data);
        }
        this.receiveBuffer.clear();

        Packet packet;
        while ((packet = this.sendBuffer.poll()) != null) {
            this.emit(mMessageEvents.getEvent(ByteUtils.bytes2int( packet.getEvent())), packet);
        }
        this.sendBuffer.clear();
    }

    /**
     *
     * @param packet 需要发送的包
     */
    private void packet(Packet packet) {
        final MySocket self = this;

        if (!self.sending) {
            self.sending = true;
            try{
                mOutputStream.write(packet.getByte());
                mOutputStream.flush();
            }catch (Exception e){
                e.printStackTrace();
                emit(SocketEvent.ERROR,e);
                onError(e);
            }finally {
                self.sending = false;
                self.processPacketQueue();
            }

        } else {
            self.packetBuffer.add(packet);
        }
    }


    ObservableEmitter<byte[]> myEmitter;
    Observable observable = Observable.create(new ObservableOnSubscribe<byte[]>() {
        @Override
        public void subscribe(ObservableEmitter<byte[]> emitter) throws Exception {
            myEmitter = emitter;
        }
    });

    private void waitFeedBack(Packet packet){

        if (!mMessageEvents.getEvent(ByteUtils.bytes2int(packet.getEvent())).equalsIgnoreCase(SocketEvent.PING)&&!mMessageEvents.getEvent(ByteUtils.bytes2int(packet.getEvent())).equalsIgnoreCase(SocketEvent.PONG)){
            Observable.intervalRange(0,mOptions.requestRetryTimes,mOptions.requestWaitTimeout,mOptions.requestWaitTimeout,TimeUnit.MILLISECONDS).takeUntil(observable.skipWhile(o->{
                byte[] data = (byte[]) o;
                return !Arrays.equals(data, packet.getSeqNum());
            })).subscribe( o-> {
                mSLog.d(TAG, "request "+o+" times");
                if ((long)o!=(mOptions.requestRetryTimes-1)){
                    if (MySocket.this.connected) {
                        //放在发送消息队列
                        MySocket.this.packet(packet);
                    } else {
                        //放在离线送消息队列
                        MySocket.this.sendBuffer.add(packet);
                    }
                }else {
                    Exception e = new RequestException("request no reply "+mOptions.requestRetryTimes+" times");
                    emit(SocketEvent.ERROR,e,packet);
                }
            });
        }

    }

    private void processPacketQueue() {
        if (this.packetBuffer.size() > 0 && !this.sending) {
            Packet packet = this.packetBuffer.remove(0);
            if (MySocket.this.connected) {
                //放在发送消息队列
                MySocket.this.packet(packet);
                waitFeedBack(packet);
            } else {
                //放在离线送消息队列
                MySocket.this.sendBuffer.add(packet);
            }
        }
    }


    /**
     *
     * @param reason 关闭之后自动重连
     */
    public void close(String reason) {
        destory();
        packetBuffer.clear();
        this.emit(SocketEvent.CLOSE, reason);
        if (this.mOptions.reconnection) {
            this.reconnect();
        }
    }

    public void destory(){
        this.connected = false;
        try{
            if(mSocket!=null){
                if (mSocket.isConnected()){
                    mSocket.shutdownInput();
                    mSocket.close();
                }
                mSocket = null;
            }
            if (mInputStream!=null){
                mInputStream.close();
                mInputStream = null;
            }

            if (mOutputStream!=null){
                mOutputStream.close();
                mOutputStream = null;
            }
            sending = false;
        }catch (Exception e){
            e.printStackTrace();
            emit(SocketEvent.ERROR,e);
            try{
                if(mSocket!=null){
                    if (mSocket.isConnected()){
                        mSocket.close();
                    }
                    mSocket = null;
                }
                if (mInputStream!=null){
                    mInputStream.close();
                    mInputStream = null;
                }

                if (mOutputStream!=null){
                    mOutputStream.close();
                    mOutputStream = null;
                }
                sending = false;
                this.onError(e);
            }catch (Exception e1){
                e.printStackTrace();
                emit(SocketEvent.ERROR,e);
            }
        }
    }

}
