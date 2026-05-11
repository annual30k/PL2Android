# 执法耳机 App Android 端开发落地方案 (第三阶段)

## 1. 项目架构设计 (Architecture)

### 1.1 技术栈选择
- **核心框架**: Android 原生 (Kotlin + Jetpack Compose)
- **开发工具**: Android Studio
- **构建工具**: Gradle + Android Gradle Plugin
- **架构模式**: MVVM + Clean Architecture，按 `data / domain / presentation` 分层
- **状态管理**: ViewModel + StateFlow / SharedFlow，DataStore 持久化 Token、设备状态、用户偏好
- **组件体系**: Material Design 3 + 执法场景私有 Compose 组件库

### 1.2 目录结构规划
```text
├── app/src/main/java/com/patrollink
│   ├── data                # 数据层
│   │   ├── remote          # Retrofit API、WebSocket、DTO
│   │   ├── local           # Room、DataStore、本地文件索引
│   │   ├── ble             # BluetoothLeScanner / BluetoothGatt 封装
│   │   ├── media           # MediaCodec、WebRTC、流媒体中继
│   │   └── repository      # Repository 实现
│   ├── domain              # 业务层
│   │   ├── model           # 设备、媒体、预警、用户领域模型
│   │   └── usecase         # 登录、绑定设备、上传证据、处置预警等用例
│   ├── presentation        # 展示层
│   │   ├── navigation      # Jetpack Navigation 路由
│   │   ├── screen          # 登录、设备、媒体、预警、SOS、我的页面
│   │   ├── component       # 私有 Compose 组件库
│   │   └── theme           # 色彩、字号、间距、形状规范
│   ├── service             # 前台服务、定位/心跳/推流保活
│   ├── worker              # WorkManager 离线补偿、文件续传、版本检查
│   └── util                # SHA-256、水印注入、坐标转换、日志
└── app/src/main/res        # 图标、权限声明、通知渠道、网络安全配置
```

---

## 2. 核心模块封装方案 (Core Modules)

### 2.1 关键链路状态机 (Critical State Management)
针对专业场景，所有关键业务必须具备明确的状态流转：

- **设备状态机**: `Disconnected` -> `Scanning` -> `Connecting` -> `Connected` -> `Syncing(同步配置)` -> `Ready`
- **上传状态机**: `Pending` -> `Hashing(计算哈希)` -> `Uploading(分片)` -> `Verifying(校验)` -> `Success/Fail`
- **预警状态机**: `Received` -> `Acknowledged(已接警)` -> `Handling(处理中)` -> `Closed`

### 2.2 异常可视化体系 (Visualized Exceptions)
全方位覆盖用户提到的异常场景，不让用户处于信息盲区：

| 异常类型           | 触发源                      | 表现形式                             | 交互建议                 |
| :----------------- | :-------------------------- | :----------------------------------- | :----------------------- |
| **网络中断**       | `ConnectivityManager.NetworkCallback` | 页面顶部全局常驻 Banner              | 点击重试 / 进入连接诊断  |
| **蓝牙关闭**       | `bleService.onError(10001)` | 设备卡片变为灰色，显示“蓝牙未开启”   | 一键跳转系统设置         |
| **WebSocket 断开** | `wsService.onClose`         | 状态栏“云端连接”图标变红并闪烁       | 自动重连逻辑，显示倒计时 |
| **推流异常**       | `streamRelay.onFail`        | 预览窗覆盖半透明黑色，显示“推流中断” | 增加“切换线路”按钮       |
| **存储不足**       | `fileService.preCheck`      | 媒体下载前强制弹窗提示               | 引导清理手机空间         |

### 2.3 关键操作二次确认 (Confirmation Strategy)
- **SOS 触发**: 采用“长按 3 秒”或“防误触滑块”，并伴随强震动反馈。
- **文件删除**: 强制勾选“我知道此操作无法撤销”后才可点击删除。
- **退出登录**: 清除所有缓存文件和 Token 的显式确认。

---

## 3. Android 硬件与系统能力方案

### 3.1 BLE 蓝牙适配 (Android BLE)
- **权限**: Android 12 及以上申请 `BLUETOOTH_SCAN`、`BLUETOOTH_CONNECT`；Android 11 及以下扫描仍需 `ACCESS_FINE_LOCATION`。
- **能力**: 使用 `BluetoothLeScanner` 扫描，`BluetoothGatt` 建立连接、发现服务、订阅特征值。
- **兼容**: 对扫描超时、系统蓝牙关闭、厂商后台限制、GATT 133 错误建立统一错误码和重试策略。
- **封装**: 统一暴露 `connect(mac/id)`, `write(cmd)`, `onNotify(data)` 接口。

### 3.2 文件系统与水印 (File & Watermark)
- **存储**: 使用应用沙盒保存执法敏感文件；需导出时通过 `MediaStore` 或 `Storage Access Framework` 受控写入。
- **索引**: 使用 Room 保存文件元数据、上传状态、哈希值和水印状态。
- **水印**: 图片使用 Bitmap 离屏处理；视频优先通过 MediaCodec 解码/编码管线或 FFmpeg/NDK 库实现隐形盲水印。

### 3.3 推流中继 (Stream Relay)
- **难点**: 移动端作为 RTSP -> RTMP 的中转站，功耗极高。
- **方案**: 优先使用 `MediaCodec` H.264 硬编硬解；低延迟对讲使用 WebRTC Native SDK。
- **保活**: 推流和对讲期间启动 Android 前台服务，展示常驻通知；网络变化后自动重建链路。

---

## 4. 接口 Mock 数据规范 (API Mocking)

为了保证前后端并行开发，定义高仿真数据格式：

```json
// 设备详情响应接口示例 (GET /api/v1/device/status)
{
  "code": 200,
  "data": {
    "deviceId": "HEADSET_001",
    "online": true,
    "battery": 85,
    "isCharging": false,
    "signal": 4, // 1-5格
    "recordingStatus": "IDLE", // IDLE, RECORDING, STREAMING
    "storage": {
      "total": 128, // GB
      "used": 42.5
    },
    "firmwareVersion": "v1.2.4"
  }
}

// 预警推送消息示例 (WebSocket Message)
{
  "type": "ALERT_PUSH",
  "payload": {
    "id": "AL_9527",
    "level": "CRITICAL", // CRITICAL, WARNING, INFO
    "title": "非法聚众预警",
    "location": [116.397, 39.908],
    "timestamp": 1715832000
  }
}
```

---

## 5. 开发建议与避坑指南

1. **单手操作优化**: 所有高频按钮（拍照、录像、对讲、SOS）均布局在屏幕底部 1/3 区域，采用大面积点击热区。
2. **离线补偿策略**: 当网络中断时，所有预警处置记录先存入本地 `Room`，恢复连接后由 `WorkManager` 按时间戳顺序自动补发。
3. **文案规范**: 
   - ❌ "太棒了，上传成功了！" -> ✅ "数据同步完成"
   - ❌ "糟糕，出错了..." -> ✅ "连接已中断 (错误代码: 403)"
   - ❌ "快去连接设备吧" -> ✅ "待接入设备"

---
**确认该方案后，我将为您输出【第四阶段：高保真文字原型说明】，为每一个页面定稿交互细节。**
