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
