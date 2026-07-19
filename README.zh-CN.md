# PL2Android 中文说明

PL2Android 是 PatrolLink 执法耳机 App 的 Android 原生实现版本，用于验证移动端核心业务流程、硬件接入边界、后台服务能力和未来后端接口契约。

技术栈：

- Kotlin
- Jetpack Compose + Material 3
- MVVM 风格状态管理
- 真实后端 REST、小脑 REST、SourceNex/UTE/BLE 与 Wi-Fi 文件传输实现

使用方式：用 Android Studio 打开当前目录，选择 `app` 配置运行即可。

## 已实现功能范围

- 登录、刷新令牌、会话恢复和安全退出流程，连接 PatrolLink 后端真实接口。
- 设备扫描、绑定、拍照指令、录音开关和对讲开关。
- 告警监听、确认、关闭、误报和请求增援处理路径。
- 媒体列表、SHA-256 校验状态、下载/上传进度状态机和删除操作。
- 日报模块，已通过小脑直连接口 `POST /api/v1/llm/report` 生成执勤日报草稿。
- 15 秒设备心跳、定位、平台指令拉取/ACK、消息和告警补拉流程。
- 低延迟、均衡、证据质量三种模式的流转发状态机；真实耳机视频等待厂家 SDK 能力。
- SOS 激活和取消流程，包含位置、录音和增援 ETA 状态。
- Spring Boot REST 契约，统一使用 `code/message/data/traceId/timestamp`。
- 分页列表契约，统一使用 `items/page/pageSize/total/hasMore`。
- 安全 token 存储、Android 权限规划、后台任务队列、证据完整性哈希等平台边界。
- 基于 OkHttp 的真实 REST 客户端和 REST-backed gateway 实现。
- 基于 OkHttp WebSocket 的实时通道骨架，用于心跳和告警推送。
- Android BLE 扫描 gateway 骨架和 BLE 指令编码器。
- 设备热点 Wi-Fi 文件服务客户端，用于文件列表、下载和上传。
- Android Keystore 加密会话存储。
- BLE、定位、相机、录音、通知等运行时权限入口。
- SOS、流传输、对讲、心跳保活相关前台服务和通知通道。
- 基于 `ConnectivityManager` 的网络监测。
- 版本检查 gateway 和离线同步引擎。

硬件和网络边界定义在 `domain/Contracts.kt` 中。正式运行使用 `RuntimeDependencyFactory` 创建真实依赖；Mock 仅保留在单元测试源码中。未配置后端或硬件通道时会返回明确失败，不会伪造业务成功。

## REST 数据契约

Android 客户端按照 Spring Boot 后端接口契约工作。Redis、MySQL、国产数据库等后端存储选型属于服务端内部实现细节，App 侧只依赖稳定的 REST DTO。测试目录中的 Mock 使用相同契约验证断网、重试和映射行为。

统一响应结构：

```json
{
  "code": 200,
  "message": "OK",
  "data": {},
  "traceId": "trace-example-0001",
  "timestamp": 1715832000
}
```

分页响应结构：

```json
{
  "items": [],
  "page": 1,
  "pageSize": 20,
  "total": 0,
  "hasMore": false
}
```

DTO 和映射逻辑位于 `app/src/main/java/com/patrollink/data/remote`。

## 真实集成切换点

- 后端 REST：`data/remote/OkHttpPatrolRestApi.kt`
- 边缘智慧小脑 REST：`data/edge/OkHttpCerebellumApi.kt`
- 后端接入 gateway：`data/RestBackedGateways.kt`
- WebSocket：`data/realtime/OkHttpWebSocketRealtimeGateway.kt`
- BLE：`data/ble/AndroidBleDeviceGateway.kt`
- Wi-Fi 文件传输：`data/file/WifiFileServiceClient.kt`
- 安全会话存储：`data/local/AndroidKeystoreSecureStore.kt`
- 离线任务持久化：`data/local/JsonFileBackgroundTaskGateway.kt`
- 前台保活服务：`service/PatrolForegroundService.kt`

小脑直连可通过构建参数或环境变量配置：

```bash
PATROL_CEREBELLUM_BASE_URL=http://127.0.0.1:8088 \
PATROL_CEREBELLUM_API_KEY=change-this-key \
./gradlew assembleDebug
```

移动端运行时配置优先读取打包内置 JSON，避免每次打包忘记传环境变量：

- 开发环境：`app/src/debug/assets/patrol-runtime.json`
- 生产环境：`app/src/release/assets/patrol-runtime.json`

配置优先级为：App 本机已保存设置 > 对应构建类型的 `patrol-runtime.json` > Gradle `BuildConfig` / 环境变量兜底。开发包默认使用 `http://10.0.2.2:8080` 访问宿主机后端，模拟器无需再额外传 `PATROL_REST_BASE_URL`。

Android 模拟器访问宿主机 Docker 时可用 `http://10.0.2.2:8088`。局域网直连工程样机时可用设备热点或 Wi-Fi Direct 分配的 `192.168.x.x` 地址；公网或跨网段访问必须改为 HTTPS/mTLS 网关。

安装后也可以在 App 内配置：进入 **我的 → 小脑连接**，填写小脑服务地址和 API Key 后保存。运行时配置会写入本机 `patrol_runtime_config`，保存后立即用于日报生成，不需要重新打包。不同民警或不同小脑设备可分别填写自己的热点/局域网地址，例如 `http://192.168.4.1:8088`。

当前安卓端日报页签会调用小脑：

```http
POST /api/v1/llm/report
Content-Type: application/json

{
  "mission_id": "mission-20260515-test",
  "report_type": "daily",
  "prefer_quality": true,
  "operator_note": "今日重点巡逻商业街区域",
  "max_tokens": 1200
}
```

App 解析 `report.content/backend/model/generated_at/requires_human_confirmation` 并在页面展示；AI 草稿默认提示需要执勤人员复核后入库。

后续生产化需要补齐真实后端 URL 和接口契约、耳机 GATT UUID、指令确认协议、Wi-Fi 热点文件 API 细节、小脑发现/配对流程以及音视频流 SDK 接入点。

## 验证方式

```bash
./gradlew clean testDebugUnitTest assembleDebug
```

当前工作区验证结果：构建成功，JVM 单元测试全部通过。由于没有连接模拟器或真机，尚未执行安装和启动冒烟测试。

## 真机安装约定

项目当前常用真机为 `SKRGYH599TDQROSO`（Redmi/MIUI/HyperOS）。这台设备会拦截普通 ADB 覆盖安装，因此后续安装 debug 包到真机时默认使用 `/data/local/tmp` 加手机侧 `pm install`：

```bash
./gradlew :app:assembleDebug

adb -s SKRGYH599TDQROSO push app/build/outputs/apk/debug/app-debug.apk /data/local/tmp/PatrolLink-debug.apk

adb -s SKRGYH599TDQROSO shell pm install -r -t --user 0 /data/local/tmp/PatrolLink-debug.apk

adb -s SKRGYH599TDQROSO shell dumpsys package com.patrollink | rg -n "lastUpdateTime|versionName|versionCode"
```

如果需要在手机文件管理器中留一份安装包，再额外推送到 Download：

```bash
adb -s SKRGYH599TDQROSO push app/build/outputs/apk/debug/app-debug.apk /sdcard/Download/PatrolLink-debug.apk
```

不要从 `/sdcard/Download` 直接执行 `pm install`，系统服务通常无法读取该 FUSE 路径，会报 `Unable to open file`。只有当 `/data/local/tmp` 的 `pm install` 也被 MIUI 返回 `INSTALL_FAILED_USER_RESTRICTED` 时，才退回到打开本地安装器并在手机上手动确认安装。
