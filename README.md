# 说明
这个是内部使用的代理库,转发了http请求,通过私有socket通道和协议通讯,通讯协议参考另一个项目.
https://github.com/fantasysea/nodesocket

# 使用说明
目前仅仅定制了反扫接口，如果有针对性的定制优化需要前后端一起开发。
透传接口目前没做，透传的意思就是按照指定结构发数据，后端直接转发，不做数据优化和处理，结果直接返回。

## MyMessageEvents
可以随意添加事件

## 初始化
```
SocketClient.getSocketClient().init(address,port);
SocketClient.getSocketClient().connect();
```

## 注册基础参数和请求接口（目前是按照form表单结构传输，其他的没有实现，如果不是from提交可以忽略）
```
    private Emitter.Listener onConnect = new Emitter.Listener() {
        @Override
        public void call(final Object... args) {
            Log.e("sea",args.toString());

            APIConfigRes.APIConfig.BackSweepBean backSweep = APIVendor.getInstance().getAPIConfig().getBack_sweep();
            APIConfigRes.APIConfig.OrderBean orderBean = APIVendor.getInstance().getAPIConfig().getOrder();

            Map<String, String> fields = new HashMap<>();
            fields.put("baseparam", "&version="+ AppUtils.getAppVersionName()+"&"+backSweep.getParam_mapping().getSn()+"="+SerialNumberReader.getInstance().getSerialNumber()+"&"+backSweep.getParam_mapping().getToken()+"="+ActivateInfoHelper.getInstance().getActivateToken());
            fields.put("backsweepurl", backSweep.getUrl());
            fields.put("queryorderurl", orderBean.getUrl());
            fields.put("rc4key", APIVendor.getInstance().getAPIConfig().getEncrypt_key());
            mSocket.send(MyMessageEvents.REGISTER,new Gson().toJson(fields).getBytes());
        }
    };
```

## 反扫接口

```
//轻轻的id和paycode做了个简单的对应
private ConcurrentMap<Integer, String> requests = new ConcurrentHashMap<Integer, String>();

if (SocketClient.getSocketClient().isConnected()){
    //auth_code=2JGdEuHng5%2B7lZzHS2Uu4vG2&sn=91800100000471&price=1&token=6c0857f0d3ec49188989e4a98f407110&version=1.4.0

    byte[] paycodeData = ToolsUtil.bigNumtoBigEndianBytespadStart(text,20);//10字节
    byte[] price = ToolsUtil.toBigEndianBytes(String.format("%06x", (int)(Float.parseFloat(mMoney)*100)));//3字节
    int seqnum = SocketClient.getSocketClient().send("backsweep",ToolsUtil.byteMergerAll(ToolsUtil.xor(paycodeData,key),price));
    requests.put(seqnum,text);
}

```

## 回调
```
    //反扫结果
    @Subscribe(threadMode = ThreadMode.MAIN)
    public void onBackSweepEvent(BackSweepEvent event) {
        if (event.getCode()==0){
            if (isFinishing()) { return;}
            actionPaySuccess("", String.valueOf(event.getPrice()/100.f), event.getOrderNo());
        }else {
            if (isFinishing()) {return;}
            BaseRes baseRes = new BaseRes();
            baseRes.code=event.getCode();
            baseRes.data = event.getOrderNo();
            backSweepFail(baseRes,event.getOrderNo(), String.valueOf(event.getPrice()/100.f), requests.get(event.getSeqNum()));
        }
    }

    //其他请求失败的时候
    @Subscribe(threadMode = ThreadMode.MAIN)
    public void onHttpProxyFailEvent(HttpProxyFailEvent event) {
        if (MyMessageEvents.events.get(ByteUtils.bytes2int(event.getPacket().getEvent())).equalsIgnoreCase(MyMessageEvents.BACKSWEEP)){

            byte[] content = event.getPacket().getContent();
            byte[] paycode = new byte[10];
            byte[] price = new byte[3];
            System.arraycopy(content,0,paycode,0,10);
            System.arraycopy(content,10,paycode,0,3);
            httpBackSweep(ToolsUtil.bigNumhex2String(ByteUtils.bytesToHexFun3(paycode)),String.valueOf(ByteUtils.bytes2Long(price)));
        }
    }
```


## 其他

配置信息在Options中，可以设置的参数参考下面
```
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
        public int seqNumLegth = 3;//seqnum，循环0 -0xffffff

        //等待返回超时时间
        public int requestWaitTimeout = 3000;
        //请求超时重试次数
        public int requestRetryTimes = 3;

        //是否输入日志
        public boolean debug = false;
    }

```
