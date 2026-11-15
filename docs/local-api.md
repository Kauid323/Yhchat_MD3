# 云湖本地 API

把 Canary 客户端已登录账号的能力，以 **JSON HTTP** 的形式暴露在本机，供 agent / 脚本调用。

> 这一层不是云湖官方接口的替代品。它只是把 App 内部已经封装好的 protobuf 接口
> 转成 JSON，省掉调用方自己实现 protobuf 编解码的成本。

---

## 快速开始

1. 打开 App → **设置 → 高级 → 本地 API**
2. 打开「本地服务器」开关
3. 默认地址：`http://127.0.0.1:8848`

验证是否可用：

```bash
curl http://127.0.0.1:8848/
```

```json
{
  "ok": true,
  "data": {
    "endpoints": [
      {"method": "GET", "path": "/conversations", "title": "会话列表", "params": "limit=20"},
      "..."
    ]
  }
}
```

---

## 通用约定

### 无需认证

**不需要 token，也不需要任何鉴权。** 安全边界是「服务默认只绑定 `127.0.0.1`」——
只有本机能连上。在设置里关掉「仅本机可访问」会绑定 `0.0.0.0`，
那时同一局域网内的设备也能访问，等于把账号数据公开出去，请自行判断。

### 响应信封

所有端点统一返回：

**成功**
```json
{"ok": true, "data": { ... }}
```

**业务失败**
```json
{"ok": false, "error": "缺少 chatId"}
```

⚠️ **业务失败也返回 HTTP 200**。这是刻意的：调用方只需要判断 `ok` 字段，
不应该把「群 ID 不存在」当成网络故障去重试。

只有协议级错误才用非 200：

| 状态码 | 含义 |
|---|---|
| 200 | 一切正常（含业务失败，看 `ok`） |
| 404 | 路径不存在 |
| 405 | 方法不对（如对 `/send` 用 GET） |
| 500 | 服务内部异常 |

### 参数风格

全部走 **query string**，GET 就能调，不需要构造 JSON body。

```bash
curl "http://127.0.0.1:8848/messages?chatId=123456&chatType=2&limit=10"
```

参数名同时接受 `camelCase` 和 `snake_case`：`chatId` 与 `chat_id` 等价。

### 分页

带 `limit` 的端点默认 20，范围 **1–100**。超出会被夹到边界值，不会报错。

### chatType 取值

| 值 | 含义 |
|---|---|
| 1 | 用户（私聊） |
| 2 | 群聊 |
| 3 | 机器人 |

---

## 端点

### `GET /` 或 `GET /endpoints`

列出全部端点。用于 agent 自动发现能力。

```bash
curl http://127.0.0.1:8848/
```

---

### `GET /conversations`

获取会话列表，按最近活跃排序。

| 参数 | 必填 | 说明 |
|---|---|---|
| `limit` | 否 | 返回条数，默认 20，最大 100 |

```bash
curl "http://127.0.0.1:8848/conversations?limit=10"
```

```json
{
  "ok": true,
  "data": {
    "total": 37,
    "count": 10,
    "items": [
      {
        "chatId": "979377289",
        "chatType": 2,
        "name": "花枫咖啡馆",
        "avatarUrl": "https://chat-img.jwznb.com/xxx.png",
        "lastMessage": "好的",
        "timestamp": 1763200000000,
        "unreadCount": 1,
        "doNotDisturb": false,
        "mentioned": false
      }
    ]
  }
}
```

| 字段 | 说明 |
|---|---|
| `total` | 服务端返回的会话总数（未截断前） |
| `count` | 本次实际返回条数 |
| `timestamp` | 毫秒时间戳 |
| `unreadCount` | 是否有未读（1 表示有） |
| `mentioned` | 是否被 @ |

> 注：模型里没有「置顶」字段，所以不返回 `isTop`。不要拿别的字段推断。

---

### `GET /messages`

获取某个会话的消息。

| 参数 | 必填 | 说明 |
|---|---|---|
| `chatId` | **是** | 会话 ID |
| `chatType` | 否 | 1/2/3，默认 2（群聊） |
| `limit` | 否 | 条数，默认 20，最大 100 |
| `msgId` | 否 | 从该消息往前翻（用于分页） |

```bash
curl "http://127.0.0.1:8848/messages?chatId=979377289&chatType=2&limit=5"
```

```json
{
  "ok": true,
  "data": {
    "count": 5,
    "items": [
      {
        "msgId": "abc123",
        "senderId": "8516939",
        "senderName": "那狗吧",
        "senderType": 1,
        "contentType": 1,
        "text": "你好",
        "imageUrl": "",
        "fileUrl": "",
        "fileName": "",
        "sendTime": 1763200000000,
        "quoteMsgId": ""
      }
    ]
  }
}
```

`contentType` 与云湖消息类型一致，常见值：

| 值 | 类型 |
|---|---|
| 1 | 文本 |
| 2 | 图片 |
| 3 | Markdown |
| 4 | 文件 |
| 7 | 表情 |
| 8 | HTML |
| 11 | 语音 |

**翻页**：把最后一条的 `msgId` 作为下一次的 `msgId` 参数。

---

### `GET /user`

用户详情。

| 参数 | 必填 | 说明 |
|---|---|---|
| `userId` | **是** | 用户 ID |

```bash
curl "http://127.0.0.1:8848/user?userId=8516939"
```

```json
{
  "ok": true,
  "data": {
    "userId": "8516939",
    "name": "那狗吧",
    "avatarUrl": "https://chat-img.jwznb.com/xxx.png",
    "isVip": 0,
    "registerTime": "2024-01-01",
    "onlineDay": 365,
    "ipGeo": "广东",
    "medals": ["元老", "活跃"]
  }
}
```

---

### `GET /group`

群聊详情。

| 参数 | 必填 | 说明 |
|---|---|---|
| `groupId` | **是** | 群 ID |

```bash
curl "http://127.0.0.1:8848/group?groupId=979377289"
```

```json
{
  "ok": true,
  "data": {
    "groupId": "979377289",
    "name": "花枫咖啡馆",
    "avatarUrl": "https://chat-img.jwznb.com/xxx.png",
    "introduction": "自由开放的技术交流群",
    "memberCount": 1867,
    "ownerId": "8516939",
    "adminIds": ["8516939", "5940358"],
    "categoryName": "技术",
    "isPrivate": false,
    "communityName": ""
  }
}
```

---

### `GET /bot`

机器人详情。

| 参数 | 必填 | 说明 |
|---|---|---|
| `botId` | **是** | 机器人 ID |

```bash
curl "http://127.0.0.1:8848/bot?botId=62708067"
```

```json
{
  "ok": true,
  "data": {
    "botId": "62708067",
    "name": "云湖助手",
    "avatarUrl": "https://chat-img.jwznb.com/xxx.png",
    "introduction": "帮你管群"
  }
}
```

---

### `GET /post`

社区文章详情。

| 参数 | 必填 | 说明 |
|---|---|---|
| `postId` | **是** | 文章 ID（数字） |

```bash
curl "http://127.0.0.1:8848/post?postId=30926"
```

```json
{
  "ok": true,
  "data": {
    "postId": 30926,
    "title": "群规",
    "content": "...",
    "contentType": 2,
    "authorId": "8516939",
    "boardId": 128,
    "boardName": "云湖趣事"
  }
}
```

`contentType`：1 = 文本，2 = Markdown。

---

### `GET /sticker-pack`

表情包详情。

| 参数 | 必填 | 说明 |
|---|---|---|
| `packId` | **是** | 表情包 ID（数字） |

```bash
curl "http://127.0.0.1:8848/sticker-pack?packId=2681"
```

```json
{
  "ok": true,
  "data": {
    "packId": 2681,
    "name": "444",
    "createBy": "8516939",
    "createTime": 1787882007,
    "authorName": "那狗吧",
    "authorId": "8516939"
  }
}
```

> 这个端点只返回表情包的元信息，**不含表情列表**。
> 表情条目需要走另一套接口，当前未暴露。

---

### `GET /devices`

当前账号已知的设备记录（含已下线的历史设备）。

无参数。

```bash
curl "http://127.0.0.1:8848/devices"
```

```json
{
  "ok": true,
  "data": {
    "count": 2,
    "items": [
      {
        "deviceId": "2df20f775e774a808e7d25af07402a98",
        "platform": "android",
        "loginTimestamp": 1763200000,
        "offlineByUser": false
      }
    ]
  }
}
```

`loginTimestamp` 是**秒**级时间戳。

---

### `POST /send`

发送消息。

| 参数 | 必填 | 说明 |
|---|---|---|
| `chatId` | **是** | 目标会话 ID |
| `text` | **是** | 消息内容 |
| `chatType` | 否 | 1/2/3，默认 2 |
| `contentType` | 否 | 默认 1（文本） |
| `quoteMsgId` | 否 | 引用某条消息 |

参数可以放 query，也可以放 JSON body，**两者等价**。

```bash
# 方式一：query
curl -X POST "http://127.0.0.1:8848/send?chatId=979377289&chatType=2&text=hello"

# 方式二：JSON body
curl -X POST "http://127.0.0.1:8848/send" \
  -H "Content-Type: application/json" \
  -d '{"chatId":"979377289","chatType":2,"text":"hello"}'
```

```json
{
  "ok": true,
  "data": {"sent": true, "chatId": "979377289", "chatType": 2}
}
```

**发送 Markdown**：`contentType=3`。

```bash
curl -X POST "http://127.0.0.1:8848/send" \
  -H "Content-Type: application/json" \
  -d '{"chatId":"979377289","chatType":2,"contentType":3,"text":"**加粗**"}'
```

---

## 配置

设置 → 高级 → 本地 API：

| 项 | 说明 |
|---|---|
| 开关 | 开启/关闭服务。**默认关闭** |
| 端口 | 1024–65535，默认 8848。改完点「应用」，服务会自动重启 |
| 仅本机可访问 | 默认开启（绑 `127.0.0.1`）。关闭后绑 `0.0.0.0`，局域网可访问 |

服务开关状态会持久化：**下次启动 App 会自动按上次的状态恢复**。

端口被占用时，界面会直接提示「端口 X 已被占用，换一个试试」。

---

## 已知限制

**单请求 body ≤ 1MB**
超过会被服务直接拒绝。`/send` 发长文本时注意。

**不支持 chunked 编码和 keep-alive**
每个连接处理一个请求后即关闭（`Connection: close`）。对 agent 场景够用，
但不要在这个端口上做长连接。

**依赖 App 已登录**
所有端点都用 App 当前登录的账号去请求。App 没登录时返回
`{"ok":false,"error":"应用未登录，无法代理该请求"}`。

**没有写操作的幂等保护**
`/send` 调用一次就发一条。重试会重复发送。

**`/sticker-pack` 不含表情列表**
只返回表情包本身的元信息。

---

## 错误处理示例

```bash
curl "http://127.0.0.1:8848/messages"
```

```json
{"ok": false, "error": "缺少 chatId"}
```

调用方只需：

```python
resp = requests.get("http://127.0.0.1:8848/messages", params={"chatId": "123"})
data = resp.json()
if not data["ok"]:
    print("失败:", data["error"])
else:
    for msg in data["data"]["items"]:
        print(msg["senderName"], msg["text"])
```
