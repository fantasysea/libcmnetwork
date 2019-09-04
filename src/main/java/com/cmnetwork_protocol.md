#请求转发协议 
该协议总体设计思路是尽量减少请求包的体积，去掉冗余数据。整体包结构如下图，请求头包含event，content-length，seq-number，content。由于我们要进行心跳，所以为了减少数据 ，ping，pong仅仅需要event部分即可。
## 总体结构
| event   | content-length($CL)   | seq-number        |       content       |
|:-------:|:---------------------:|:-----------------:|:-------------------:|
| <num>(2)|   <num>(2)            | <num>(3)          | <buffer>($CL)       |

### 特殊包（ping，pong）

| event   |
|:-------:|
| <num>(2)|

## event说明
event是2个字节的数字，ping，pong是0和1
```js
//事件
EVENTS = {
  PING: [0, 'ping'],
  PONG: [1, 'pong'],
  TRANSPARENT:[2, 'transparent'],
  REGISTER: [3, 'register'],
  BACKSWEEP: [4, 'backsweep'],
  QUERYORDER: [5, 'queryorder'],
}
```
### transparent
透传数据，客户端按照axios的格式组装数据,填充到content中来，然后按照结构发到服务器，服务器拿到content的数据直接发起http请求，返回的数据也填充到content中去。

### REGISTER content
REGISTER 的content是json，
```js
{
    baseparam, // 请求参数，每次请求都带上的参数
    rc4key,     // rc4 加密的key
    backsweepurl,   // 反扫地址
    queryorderurl   // 查询地址
}
```


### 反扫  

#### 请求的content         
|   order_no     | price   |
|:-----------:|:----------:|
|   <num>(10)  |  <num>(3) |

#### 返回的content  
|  code    |   price     | order_no   |
|:--------:|:-----------:|:----------:|
| <num>(2) |   <num>(3)  |  <num>(10) |

#### 其它content
|     string     |
|:--------------:|
| <buffer>($CL)  |