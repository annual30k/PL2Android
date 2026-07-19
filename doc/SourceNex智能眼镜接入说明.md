# SourceNex 智能眼镜接入说明

## 已接入能力

- 识别 `SourceNex-*`、`Aig-Glass*`、`Aig-*` 设备，并从旧 UTE/系统音频别名中去重。
- 使用 `BluetoothScanHelper` 扫描、系统配对、取消配对。
- 使用 `AigClient` 同时建立 SPP 与 GATT 控制通道。
- 拍照：`ReqCamCapture`。
- 录像开始/停止：`ReqCamRecord`、`ReqCamRecordStop`。
- 录音开始/停止：`ReqMicRecord`、`ReqMicRecordStop`。
- 照片/视频列表：`ReqPicList`、`ReqVidList`。
- 当前连接期间产生的录音通过 `MediaFile` 事件进入设备媒体列表。
- 文件通过 `AigClient.download()` 保存到 `files/patrol_media/sourcenex/`，写入 SHA-256 和水印完整性侧车文件，并加入 Room 媒体索引。
- 支持本地删除、设备端 `ReqFileDel` 删除和后续云端上传路由。

## 实时对讲接口核查结论

对“秒时”0.27.2 APK 中恢复的官方 SDK 做了 JAR 类清单和公开方法反编译核查，结论是：**当前 SDK 支持文件型麦克风录音，不支持实时双向对讲**。

| 核查项 | SDK 实际能力 | 结论 |
| --- | --- | --- |
| 麦克风 | `ReqMicRecord`、`ReqMicRecordStop`，任务类型为 `AUDIO_RECORD` | 录音文件能力 |
| 相机直播 | 仅发现 `ReqCamLiveStop`，未发现配套的公开音频流/双向对讲启动协议 | 不能组成实时对讲 |
| `AigClient` | 公开连接、下载、状态流、消息发送与回调方法 | 没有 PCM/Opus 实时收发或扬声器播放接口 |
| `AigMessage.MessageCase` | 包含录音开始/停止消息 | 没有 intercom、talk、speaker、voice-call 或 WebRTC 消息 |
| `Hmd` | 有音频路由状态和音量等属性 | 仅是设备状态，不是双向音频数据通道 |

JAR 内部虽然包含通用的 `connect.duplex` Socket 收发封装和 `PCMUtils` 压缩工具，但依赖关系显示它们服务于 `ProtocolClient/Reader/Sender` 的通用消息传输；公开 `AigClient`、`Hmd` 和完整 `AigMessage.MessageCase` 均没有建立、接收、播放实时音频流的控制方法或协议消息，不能据此推断设备开放了实时对讲。

因此 PatrolLink 已将“设备录音”和“实时对讲”拆开：录音继续通过真实 SDK 指令执行；平台和移动端不再把录音成功显示成实时对讲成功。待厂商提供双向音频采集、播放、编码、传输及 ACK 协议后，再开放实时对讲入口。手机标准蓝牙 HFP 路由即便可以承载手机 App 的 VoIP，也不等同于 SourceNex SDK 提供硬件实时对讲接口。

## 固件兼容处理

当前 `SourceNex-6240` 固件会执行拍照和停止类命令，但部分命令不返回开发指南中的 `RES_*` ACK。实现将 ACK 作为可选确认，以 `Hmd.tasks` 状态和后续 `MediaFile` 产出为最终结果，避免把已经执行成功的命令误报为失败。

SDK 仅公开照片和视频历史列表接口，没有录音历史列表接口。因此录音会在本次连接收到 `MediaFile` 后立即进入 PatrolLink；断开前未同步、且不是本次连接产生的旧录音，当前公开 SDK 无法枚举。

## SDK 构件说明

收到的 `智能眼镜SDK开发指南.doc` 没有附带文档中提到的离线 SDK ZIP/AAR。本次使用真机已安装的官方“秒时”0.27.2 APK 恢复 `net.sourcenex.aig` SDK 字节码，隔离保存为 `app/libs/sourcenex-aig-sdk-v2-recovered.jar`。协议代码要求 `protobuf-java 4.31.1`，文件通道要求 Netty HTTP 4.1.115.Final。

生产发布前应向厂商索取 `releaseClientSdk` 生成的正式离线包（`client_sdk_lib`、`event_lib`、`msg_lib` AAR 和 POM），用正式构件替换 recovered JAR，并保持现有 `data/sourcenex` 适配层不变。

## 2026-07-17 真机验证

设备：`SourceNex-6240 / 22:22:CC:60:9C:E6`。

- PASS：系统配对、SPP、GATT、MTU 512、设备在线。
- PASS：拍照，生成 `20260717_020242_0.jpg`，403.5 KB。
- PASS：录像开始/停止，生成 `20260717_020251_3.mp4`，746.3 KB，4 秒。
- PASS：录音开始/停止，生成 `20260717_020255_6.m4a`，398.5 KB，3 秒。
- PASS：三类设备文件列表。
- PASS：照片下载到 PatrolLink、SHA-256 校验、Room 本地媒体索引。
- PASS：读取设备电量 99% 和固件 `EM-SW3030-Aurora,IA,1.1-140.1-V1.0.47-20250521-160000`。
