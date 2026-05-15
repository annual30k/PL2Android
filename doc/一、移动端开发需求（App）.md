# 一、移动端开发需求（App）

## 1）整体定位

移动端主要承担：

- 执法耳机接入与控制
- 媒体采集与文件处理
- 预警接收与处置联动
- 调用边缘小脑生成执勤日报草稿
- SOS 上报
- 流媒体中继与音频对讲
- 与云端保持会话、心跳、位置、状态同步

文档中明确 App 模块拆分为：**登录页、设备页、媒体页、预警页、日报页、SOS 页、我的页**，网络层包含 **REST API + WebSocket + BLE Service + Wi-Fi File Service + Stream Relay Service + Cerebellum REST**。

## 2）移动端功能模块拆分

### A. 基础登录与会话

- 账号密码登录
- Access Token / Refresh Token 获取
- Token 安全存储（Android Keystore + Encrypted DataStore）
- 获取当前用户信息
- 启动后拉取系统配置
- 注册设备会话
- 建立 WebSocket 长连接
- 心跳保活

对应文档依据：App 登录流程、Token 安全存储、配置拉取、会话注册、心跳启动。 

### B. 设备接入与 BLE 通信

- 扫描 BLE 设备
- 过滤指定 Service UUID
- 发起配对
- 绑定设备
- 建立 GATT 长连接
- 订阅状态、电量等特征值
- 展示在线状态、电量、固件、信号强度
- 定时上报设备状态到云端
- 断线重连、离线检测

页面：

- 设备列表页
- 设备详情页
- 绑定结果弹窗

技术：

- Android：BluetoothLeScanner、BluetoothGatt
- 权限：Android 12+ 使用 `BLUETOOTH_SCAN` / `BLUETOOTH_CONNECT`，Android 11 及以下扫描兼容 `ACCESS_FINE_LOCATION`

这些是典型移动端专属需求，PC 端不承担。

### C. 媒体采集与文件管理

- 下发拍照/录像/录音控制指令
- 查询耳机端文件目录
- 获取文件列表 JSON
- 预览图片、视频封面
- 删除设备文件
- 下载设备文件到手机沙盒
- 上传文件到云端
- 计算 SHA-256
- 写入隐形水印
- 记录上传进度

页面：

- 媒体控制页
- 设备文件页
- 手机本地文件页
- 上传进度页

这个模块本质是“手机作为设备控制器与证据中转站”。

### D. 流媒体中继与音频链路

- 从耳机拉取 RTSP
- App 做解复用/重打包
- 向媒体服务器推 RTMP / WebRTC
- 推流状态监控
- 推流异常重连
- 配合 Web 端建立双向音频对讲

技术：

- Android：MediaCodec
- WebRTC Native SDK
- 前台服务：推流、对讲、心跳保活期间使用 Foreground Service + 常驻通知

这一块也是移动端核心，因为文档定义的是 **“耳机 → App 中继 → 云端/公网 → Web”**。

### E. 预警与 SOS

从总体模块拆分里，App 还包含：

- 预警页
- SOS 页
- 接收预警推送
- 关联处置动作
- 紧急上报

虽然你给我的可见片段里后续详细模块被截断了，但从模块总架构和数据链路描述看，App 是预警消息接收与现场执行端。

### F. 我的 / 版本 / 更新

- 我的页
- 版本页
- 版本检测
- 更新弹窗

文档后部还能看到 App 版本检查与更新步骤。

### G. 日报 / 小脑报告生成

- 日报页
- 输入或自动生成 `mission_id`
- 填写人工补充说明
- 调用小脑 `POST /api/v1/llm/report`
- 固定使用 `report_type=daily`
- 展示日报正文、模型、生成后端、生成时间
- 明确提示 AI 草稿需要执勤人员复核后入库

这个模块用于把现场采集、识别候选、人工备注和小脑本地大模型报告能力连接起来。弱网或离线执勤场景下，日报优先由小脑本地生成，网络恢复后再同步到后台。

## 3）移动端技术栈

- 开发语言：Kotlin
- UI：Jetpack Compose + Material Design 3
- 架构：MVVM + Clean Architecture
- 异步与状态：Kotlin Coroutines + Flow + ViewModel
- 依赖注入：Hilt
- 网络通信：Retrofit + OkHttp + WebSocket + 小脑直连 REST
- 本地存储：Room + DataStore + Android Keystore
- BLE：BluetoothLeScanner / BluetoothGatt
- 文件：Wi-Fi File Service、本地沙盒、MediaStore / Storage Access Framework
- 流媒体：MediaCodec / WebRTC Native SDK
- 后台任务：Foreground Service + WorkManager

 

## 4）移动端一句话总结

移动端是 **“设备控制 + 现场采集 + 本地处理中转 + 推流上报 + 预警执行”** 的执法作业端。
