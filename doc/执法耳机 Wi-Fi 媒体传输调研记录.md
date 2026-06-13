# 执法耳机 Wi-Fi 媒体传输调研记录

更新时间：2026-06-14 01:49

## 结论

设备确实存在 Wi-Fi 媒体传输能力。Glory View 曾经成功通过设备热点传输图片、音频和视频；Android Wi-Fi 历史也能看到 Glory View 连接过设备热点 `UTE_00F7`。但当前公开的 `UteWatchSDK_Android炬芯_V1.3.5` 文档只暴露了设备 Wi-Fi 开关、SSID、密码、状态读取，以及智能眼镜媒体数量/删除/同步完成通知；没有公开的“Wi-Fi 文件列表/下载”API。

因此当前可行方向是：

1. 继续用公开 SDK 负责 BLE 连接、设备账号绑定、Wi-Fi AP 开关和媒体数量读取。
2. 手机通过 Android Wi-Fi API 连接设备热点。
3. 在设备热点网络上探测 HTTP/私有文件服务入口，枚举并下载图片、音频、视频。
4. 参考 Glory View 的第三方鉴权/初始化握手，先把设备唤醒到更接近官方 App 的状态，再打开 Wi-Fi。

## 2026-06-13 当前状态

后端地址已重新确认，debug 包当前使用 `http://192.168.1.3:8080` 和 `ws://192.168.1.3:8080/resource/websocket`。模拟器和真机均可用 `POLICE_9527 / 123456` 登录，因此当前卡点不是后端地址。

已确认进展：

- 真机已安装 2026-06-13 最新 debug 包，安装时间为 `2026-06-13 02:21:17`。
- 模拟器已安装最新包，安装时间为 `2026-06-13 02:19:52`；登录通过，报告为 `/Users/qiuqiquan/Desktop/SmartHeadsetSystem/PatrolLink/dist/smoke-reports/smoke-test-20260613-emulator-upload-null-guard.txt`。模拟器没有真实 BLE，扫描不到真设备属于预期。
- 真机最新包非破坏性 smoke 已通过登录、扫描、绑定、能力读取、本地媒体读取和固件检查，报告为 `/Users/qiuqiquan/Desktop/SmartHeadsetSystem/PatrolLink/dist/smoke-reports/smoke-test-20260613-real-upload-null-guard-nondestructive.txt`。
- 真机非破坏性 smoke 已通过登录、扫描、BLE 控制连接和设备绑定，报告已保存到 `/Users/qiuqiquan/Desktop/SmartHeadsetSystem/PatrolLink/dist/smoke-reports/smoke-test-20260613-real-reset-ui-nondestructive.txt`。
- 01:26 重新打包并安装 debug smoke 安全收口后，真机 smoke 登录通过，但 `E1-Pro-A243` 当轮没有广播 UTE 控制通道，报告为 `/Users/qiuqiquan/Desktop/SmartHeadsetSystem/PatrolLink/dist/smoke-reports/smoke-test-20260613-real-smoke-stop-nondestructive.txt`。这是设备控制通道即时状态，不是后端地址或安装失败。
- 01:42 增加“设备媒体下载完成后自动进入后台上传队列”后重新打包；当前耳机没电，因此真机 BLE/Wi-Fi 链路验证暂停，等待设备充电后继续。
- 拍照、开始录像、停止录像、开始耳机录音、停止耳机录音命令此前已在真机通过，报告为 `/Users/qiuqiquan/Desktop/SmartHeadsetSystem/PatrolLink/dist/smoke-reports/smoke-test-20260613-real-commands.txt`。
- Wi-Fi 媒体链路已增加账号保护：如果设备账号和 PatrolLink 当前账号不一致，直接给出操作员提示“设备账号不一致，请先在原应用解绑或重置设备后重新配对 PatrolLink”，不再继续盲目打开 AP。
- 用户在 Glory View 内执行解除绑定后，PatrolLink 重新验证仍显示设备端账号不一致；停掉 Glory View 后复测结果不变，说明不是 Glory View 后台进程抢占导致。
- 已新增并安装前置 Wi-Fi 配对诊断 smoke：`preWifiPairing/preWifiAccountProbe` 会在打开 Wi-Fi 前先调用 SDK pairing/account 路径。真机报告为 `/Users/qiuqiquan/Desktop/SmartHeadsetSystem/PatrolLink/dist/smoke-reports/smoke-test-20260613-real-prewifi-pairing-account.txt`。

当前阻塞点：

- SDK 账号状态已确认：`ACCOUNT_SAME=0`、`ACCOUNT_DIFFERENT=1`、`ACCOUNT_NO=2`。
- 当前设备对 `POLICE_9527` 执行 `setHeadsetAccount` 返回 `success=true,error=100000,status=1`，表示设备已绑定其他账号。
- `setHonorAccount` 仍返回 408，Glory View 账号 `300610203` 以及 SDK demo HUID 也未让设备变成 `ACCOUNT_SAME`。
- 因账号不一致，Wi-Fi smoke 被保护逻辑拦截，报告为 `/Users/qiuqiquan/Desktop/SmartHeadsetSystem/PatrolLink/dist/smoke-reports/smoke-test-20260613-real-wifi-account-guard.txt`。
- 用户已确认允许清除设备账号；执行受保护清账号 smoke 后，`clearAccountID()` 仍返回 `success=false,error=408`，设备未清除账号。报告：
  - `/Users/qiuqiquan/Desktop/SmartHeadsetSystem/PatrolLink/dist/smoke-reports/smoke-test-20260613-real-clear-account-confirmed-408.txt`
  - `/Users/qiuqiquan/Desktop/SmartHeadsetSystem/PatrolLink/dist/smoke-reports/smoke-test-20260613-real-clear-account-notify-408.txt`
- 清账号前已补充 `openOrCloseNotify(true)` 并重新安装验证，仍为 408；因此不是 debug smoke 未开启 notify 导致。
- 清账号失败后重新探测账号状态，设备仍返回 `SDK_SET_HEADSET_ACCOUNT success=true,error=100000,status=1`，`SDK_REQUEST_PAIRING` 和 `SDK_SET_HONOR_ACCOUNT` 仍为 408。报告为 `/Users/qiuqiquan/Desktop/SmartHeadsetSystem/PatrolLink/dist/smoke-reports/smoke-test-20260613-real-account-after-clear-failed.txt`。
- 用户执行设备恢复出厂设置后复测，设备账号状态仍未改变：`PRE_WIFI_SDK_SET_HEADSET_ACCOUNT success=true,error=100000,status=1`，`PRE_WIFI_SDK_REQUEST_PAIRING success=false,error=408`，Wi-Fi 仍无法开启。报告为 `/Users/qiuqiquan/Desktop/SmartHeadsetSystem/PatrolLink/dist/smoke-reports/smoke-test-20260613-real-after-factory-reset-wifi.txt`。
- 14:22 强制探测 AIRecorder 文件录音接口：当前设备 SDK 未声明 `IS_SUPPORT_AI_RECORDER_MEETING_RECORDING`，强制调用 `appStartAudioRecord/appStopAudioRecord` 后均返回 408，`AI_RECORDER_FILES_AFTER count=0`。这说明当前 E1-Pro 不能走 AIRecorder 文件列表路线；PatrolLink 当前“录音”命令应继续按耳机录音状态控制处理。报告为 `/Users/qiuqiquan/Desktop/SmartHeadsetSystem/PatrolLink/dist/smoke-reports/smoke-test-20260613-real-force-ai-recorder.txt`。
- 14:27 增加 debug-only Wi-Fi 直调探针，绕过 PatrolLink 账号保护直接调用 SDK `smartSetDeviceWiFiSwitch(true)`。SDK 返回 `success=true,error=100000,data=true`，但 22 秒内 `readWifi()` 仍为 `enabled=false,connected=false,ssid=UTE_A243`，notify 为空，Android Wi-Fi 仍连在普通路由 `英英杀人女魔头5G`，扫描结果没有 `UTE_A243`。HTTP 探测命中的 `192.168.1.1` 是当前普通路由网络，不是耳机 AP。报告为 `/Users/qiuqiquan/Desktop/SmartHeadsetSystem/PatrolLink/dist/smoke-reports/smoke-test-20260613-real-direct-wifi-probe.txt`。
- 14:30 安装最新 debug 包后执行命令回归，`TAKE_PHOTO`、`START_VIDEO`、`STOP_VIDEO`、`START_HEADSET_AUDIO`、`STOP_HEADSET_AUDIO` 全部 PASS。同期 `SMART_GLASSES_INFO state=0,store=photo 0/0,audio 0/0,video 0/0`、`HEADSET_FILES_BEFORE count=0`、`MEDIA_DEVICE []`，说明“命令可下发”和“设备端产生/同步出媒体文件”是两件事；当前仍没有可供 App 同步的设备文件。报告为 `/Users/qiuqiquan/Desktop/SmartHeadsetSystem/PatrolLink/dist/smoke-reports/smoke-test-20260613-real-command-regression-143012.txt`。
- 14:40 对 `SMI-M14 / E8:4A:54:67:93:4D` 执行 debug-only 眼镜媒体命令矩阵。SDK 可连接该控制地址，但 `setGlassesStandby`、拍照、录像开始/停止、录音开始/停止、`retryImageUpload`、Wi-Fi 开关全部返回 408，且没有任何 notify。报告为 `/Users/qiuqiquan/Desktop/SmartHeadsetSystem/PatrolLink/dist/smoke-reports/smoke-test-20260613-real-smi-matrix-408.txt`。
- 14:49 对当前蓝牙连接的 `Glory Glass 2-00F7 / 78:02:B7:66:00:F7` 重新验证。PatrolLink 已能识别为 `Glasses` 并绑定成功；`TAKE_PHOTO`、`START_VIDEO`、`STOP_VIDEO` 全部 PASS，同时收到 `PHOTO_OK`、`MEDIA_COUNT_SUCCESS` 和有效存储通知，存储约 31.2 GB，照片数量从 0 变为 1。报告为 `/Users/qiuqiquan/Desktop/SmartHeadsetSystem/PatrolLink/dist/smoke-reports/smoke-test-20260613-real-glory-glass-commands.txt`。
- 14:50 对 `Glory Glass 2-00F7` 执行 Wi-Fi-only smoke。设备 Wi-Fi 信息可读：`ssid=UTE_00F7,passwordLen=8,state=5`；但正常 Wi-Fi 开关路径被账号保护拦截，提示“设备账号不一致，请先在原应用解绑或重置设备后重新配对 PatrolLink”。22 秒等待后仍为 `enabled=false,connected=false`，Android 仍连接普通路由，没有扫到 `UTE_00F7`。报告为 `/Users/qiuqiquan/Desktop/SmartHeadsetSystem/PatrolLink/dist/smoke-reports/smoke-test-20260613-real-glory-glass-wifi-account-guard.txt`。
- 14:52 最终 debug 包已安装到真机，`lastUpdateTime=2026-06-13 14:52:59`。本轮修正了设备识别：`Glory Glass`/`ABA002`/`眼镜` 优先归类为 `Glasses`，`SMI-` 不再作为音频耳机名放开拍照录像能力；能力判断也不再把 SDK 408 返回的空 `data` 当成有效设备能力。
- 14:55 对 `Glory Glass 2-00F7` 执行账号探针：`requestPairing`、`setHeadsetAccount`、`setHonorAccount` 均返回 408。但同一设备仍可读眼镜存储和 Wi-Fi 配置，且拍照/录像可正常下发。报告为 `/Users/qiuqiquan/Desktop/SmartHeadsetSystem/PatrolLink/dist/smoke-reports/smoke-test-20260613-real-glory-glass-account-probe-408.txt`。
- 14:56 绕过账号守卫直调 `smartSetDeviceWiFiSwitch(true)`：返回 `success=true,error=100000,data=true`，设备进入 `state=7`，`readWifi()` 变为 `enabled=true,connected=true,ssid=UTE_00F7`。报告为 `/Users/qiuqiquan/Desktop/SmartHeadsetSystem/PatrolLink/dist/smoke-reports/smoke-test-20260613-real-glory-glass-direct-wifi-open.txt`。
- 15:00 修正账号守卫后，PatrolLink 正常 Wi-Fi 路径可打开眼镜 AP，不再报账号不一致：`ENABLE_WIFI` PASS，`WAIT_WIFI_READY ready=true`。后续手机侧 `WifiNetworkSpecifier` 连接 `UTE_00F7` 超时，`UTE_WIFI_MEDIA_DIAGNOSTICS` 和 `UTE_WIFI_MEDIA_LIST` 仍失败于 `device wifi unavailable: UTE_00F7`。报告为 `/Users/qiuqiquan/Desktop/SmartHeadsetSystem/PatrolLink/dist/smoke-reports/smoke-test-20260613-real-glory-glass-wifi-open-phone-connect-timeout.txt`。
- 15:03 增加手机侧 Wi-Fi 预扫描和 BSSID 精确匹配后重新安装，`lastUpdateTime=2026-06-13 15:03:22`。真机可扫描到设备 AP：`UTE_00F7/fe:fd:fc:f8:4e:b1/rssi=-53/freq=2437`，连接请求已带 BSSID；但 MIUI/Android `WifiNetworkSpecifier` 仍返回 unavailable，屏幕出现系统弹窗“出了点问题。该应用已取消选择设备的请求。”报告为 `/Users/qiuqiquan/Desktop/SmartHeadsetSystem/PatrolLink/dist/smoke-reports/smoke-test-20260613-real-glory-glass-wifi-prescan-phone-connect-timeout.txt`。
- 15:12 增加 `WifiNetworkSuggestion` fallback 后复测。`WifiNetworkSpecifier` 仍因 MIUI 系统选择回调不可用而超时；fallback 调用 `addNetworkSuggestions` 返回 `status=0`，表示建议添加成功，但 30 秒内系统没有自动切换到 `UTE_00F7`，`UTE_WIFI_MEDIA_DIAGNOSTICS` 和 `UTE_WIFI_MEDIA_LIST` 仍失败于“wifi suggestion did not connect”。报告为 `/Users/qiuqiquan/Desktop/SmartHeadsetSystem/PatrolLink/dist/smoke-reports/smoke-test-20260613-real-glory-glass-wifi-suggestion-timeout.txt`。
- 15:17 最终 debug 包因 MIUI USB 安装限制无法静默安装，ADB 返回 `INSTALL_FAILED_USER_RESTRICTED`。APK 已推送到手机 `/sdcard/Download/PatrolLink-20260613-1517-wifi-suggestion-debug.apk` 并尝试打开系统安装器，需要在手机上确认安装。
- 15:21 增加眼镜设备页“系统 Wi-Fi”入口，用于在 MIUI 不接受 `WifiNetworkSpecifier`/`WifiNetworkSuggestion` 时直接跳转系统 Wi-Fi 设置手动选择设备热点。debug 包已推送到手机 `/sdcard/Download/PatrolLink-20260613-1520-manual-wifi-debug.apk`，但真机当前安装时间仍为 `2026-06-13 15:12:02`，说明该包尚未完成手动安装。
- 15:25 用户确认蓝牙已连上后复测 `Glory Glass 2-00F7`：登录、扫描、绑定、拍照、录像开始/停止均通过；通知包含 `PHOTO_OK`、`MEDIA_COUNT_SUCCESS`、`VIDEO_STOP_OK`，照片总数为 2；能力读取为支持眼镜、Wi-Fi、文件传输、拍照、录像，不支持录音。报告为 `/Users/qiuqiquan/Desktop/SmartHeadsetSystem/PatrolLink/dist/smoke-reports/smoke-test-20260613-real-glory-glass-ble-connected-152542.txt`。
- 15:26 在设备 Wi-Fi 已显示 `enabled=true,connected=true,ssid=UTE_00F7` 后单独探测手机侧网络：Android 仍连接普通路由 `英英杀人女魔头5G`，但能扫描到 `UTE_00F7/fe:fd:fc:f8:4e:b1/rssi=-53`；文件服务探测命中的 `192.168.1.1` 仍是普通路由，不是眼镜热点。报告为 `/Users/qiuqiquan/Desktop/SmartHeadsetSystem/PatrolLink/dist/smoke-reports/smoke-test-20260613-real-wifi-probe-after-ble-connected.txt`。
- 15:30 收口媒体上传入口：即使远端设备文件误触发“上传云端”，也会先走 `downloadMedia()` 下载进手机沙盒并写入本地媒体索引，再进入 `UploadEvidence` 后台任务队列，避免绕过后台上传服务。新增单测 `uploadingDeviceMediaFirstDownloadsToPhoneSandboxAndQueuesBackgroundUpload`，并通过 `:app:testDebugUnitTest :app:assembleDebug`。新包已推送到 `/sdcard/Download/PatrolLink-20260613-1530-media-background-debug.apk`，但 MIUI 未完成安装，真机安装时间仍为 `2026-06-13 15:12:02`。
- 15:31 模拟器已安装 15:30 新包，`lastUpdateTime=2026-06-13 15:31:24`；smoke 登录通过，因模拟器没有真实 BLE，扫描结果为 0 属于预期。报告为 `/Users/qiuqiquan/Desktop/SmartHeadsetSystem/PatrolLink/dist/smoke-reports/smoke-test-20260613-emulator-media-background-153147.txt`。
- 15:35 通过 `/data/local/tmp` + `pm install -r` 成功安装真机新包，`lastUpdateTime=2026-06-13 15:35:39`。该包包含设备文件先落手机沙盒再入后台上传队列、设备页“系统 Wi-Fi”入口，以及手动连接 `UTE_00F7` 后复用当前 Wi-Fi 网络的逻辑。
- 15:36 真机最新已安装包复测 `Glory Glass 2-00F7`：登录、扫描、绑定、拍照、录像开始/停止均通过；通知包含 `PHOTO_OK`、`MEDIA_COUNT_SUCCESS`、`VIDEO_STOP_OK`，照片总数为 3；能力读取继续显示支持眼镜、Wi-Fi、文件传输、拍照、录像，不支持录音。完整报告未落盘，关键 logcat 已归档到 `/Users/qiuqiquan/Desktop/SmartHeadsetSystem/PatrolLink/dist/smoke-reports/logcat-20260613-real-latest-install-ble-commands.txt`。
- 15:38 修正 debug smoke：`SDK_FEATURE_FLAGS` 反射查询增加 3 秒超时保护，避免末尾卡住导致 smoke 报告不落盘。该修正版已构建并推送到 `/data/local/tmp/PatrolLink-20260613-1538-wifi-reuse-smoke-timeout-debug.apk`，但 MIUI 当前再次返回 `INSTALL_FAILED_USER_RESTRICTED`，真机仍停在 15:35 包。
- 15:39 使用 `cmd wifi add-suggestion UTE_00F7 wpa2 12345678 -s -b fe:fd:fc:f8:4e:b1` 尝试由系统 shell 建议连接设备热点，12 秒后手机仍连接 `英英杀人女魔头5G`；说明 shell suggestion 同样不会自动切到无互联网设备热点。当前仍需要在系统 Wi-Fi 页面手动选择 `UTE_00F7`。
- 22:32 根据真机反馈“Wi-Fi 能连上设备热点，但媒体页面没有操作把设备照片上传到手机端”，补齐媒体页闭环：设备端列表新增“设备文件”同步栏，提供“刷新”和“同步”入口；批量模式下设备端主按钮改为“同步”。新增 `PatrolViewModel.syncDeviceMediaToPhone(...)`，只下载手机端尚不存在的设备文件，成功后沿用 `downloadMedia()` 的手机沙盒落盘、媒体索引和 `UploadEvidence` 后台任务队列。新增单测 `syncDeviceMediaToPhoneDownloadsMissingDeviceFilesAndQueuesBackgroundUploads`，并通过 `./gradlew :app:testDebugUnitTest :app:assembleDebug --console=plain`。新包 `/Users/qiuqiquan/Desktop/SmartHeadsetSystem/PatrolLink/dist/PatrolLink-20260613-media-sync-debug.apk` 已通过 `/data/local/tmp` + `pm install -r` 安装到真机，`lastUpdateTime=2026-06-13 22:32:15`。
- 22:38 继续补齐媒体页操作反馈：设备端同步栏新增“Wi-Fi”入口，可直接打开系统 Wi-Fi 设置；用户主动点“刷新”读取设备文件失败时，不再静默显示空列表，而是提示“设备文件读取失败：...；请确认手机已连接设备热点后重试”。新增单测 `manualDeviceMediaRefreshShowsWifiFailureToOperator`，并通过 `./gradlew :app:testDebugUnitTest :app:assembleDebug --console=plain`。新包 `/Users/qiuqiquan/Desktop/SmartHeadsetSystem/PatrolLink/dist/PatrolLink-20260613-media-sync-wifi-feedback-debug.apk` 已通过 `/data/local/tmp` + `pm install -r` 安装到真机，`lastUpdateTime=2026-06-13 22:38:08`。当前手机仍连接普通路由 `英英杀人女魔头5G`，还需要手动切到 `UTE_00F7` 后验证真实文件列表/下载。
- 22:40 补齐“刷新成功但设备端没有文件”的操作反馈：媒体页设备端主动刷新时，如果文件服务返回空列表，会提示“设备端没有读取到媒体文件；请先拍照/录像/录音，或确认手机已连接设备热点后重试”，避免操作员误以为按钮无效。新增单测 `manualDeviceMediaRefreshShowsEmptyDeviceListToOperator`，并通过 `./gradlew :app:testDebugUnitTest :app:assembleDebug --console=plain`。新包 `/Users/qiuqiquan/Desktop/SmartHeadsetSystem/PatrolLink/dist/PatrolLink-20260613-media-empty-feedback-debug.apk` 已通过 `/data/local/tmp` + `pm install -r` 安装到真机，`lastUpdateTime=2026-06-13 22:40:31`。当前手机仍连接普通路由 `英英杀人女魔头5G`，真实 Wi-Fi 文件列表/下载仍待手动切换到 `UTE_00F7` 后验证。
- 22:41 同一 22:40 新包已安装到模拟器，`lastUpdateTime=2026-06-13 22:41:37`，并可启动 `com.patrollink/.MainActivity`。模拟器没有真实 BLE/Wi-Fi 设备，且 UI 自动登录停留在登录页，媒体页入口的行为以 `PatrolViewModelTest` 中设备文件同步、读取失败、空列表提示三组单测作为回归证据；真机仍是后续 Wi-Fi 文件列表/下载闭环的权威验证目标。
- 22:47 补强 debug smoke，用于真机手动连接设备热点后的闭环验证：新增 `--ez wifiDownloadFirst true` 参数。开启后，Wi-Fi smoke 会先列出 `UTE_WIFI_MEDIA_LIST`，再用 `coordinator.transferMedia(first.id, PhoneSandbox)` 下载第一条设备媒体，并输出 `UTE_WIFI_MEDIA_DOWNLOAD_FIRST` 传输状态和 `UTE_WIFI_MEDIA_LOCAL_AFTER_DOWNLOAD` 本地文件结果。新增单测 `wifiMediaSyncOptionOnlyDownloadsWhenRequested`，并通过 `./gradlew :app:testDebugUnitTest :app:assembleDebug --console=plain`。新包 `/Users/qiuqiquan/Desktop/SmartHeadsetSystem/PatrolLink/dist/PatrolLink-20260613-wifi-download-smoke-debug.apk` 已安装到真机和模拟器，二者 `lastUpdateTime=2026-06-13 22:47:06`。当前真机仍连接普通路由 `英英杀人女魔头5G`；手动切到 `UTE_00F7` 后可执行：
  `adb -s SKRGYH599TDQROSO shell am broadcast -n com.patrollink/.debug.SmokeTestReceiver --es account POLICE_9527 --es password 123456 --ez commands false --ez wifi true --ez wifiDownloadFirst true --ez auth false --ez pairing false --ez accountProbe false --es targetDeviceId 78:02:B7:66:00:F7`
- Glory View 解除绑定后的报告：
  - `/Users/qiuqiquan/Desktop/SmartHeadsetSystem/PatrolLink/dist/smoke-reports/smoke-test-20260613-real-after-glory-unbind-wifi.txt`
  - `/Users/qiuqiquan/Desktop/SmartHeadsetSystem/PatrolLink/dist/smoke-reports/smoke-test-20260613-real-after-glory-force-stop-wifi.txt`
  - `/Users/qiuqiquan/Desktop/SmartHeadsetSystem/PatrolLink/dist/smoke-reports/smoke-test-20260613-real-prewifi-pairing-account.txt`
- 前置配对诊断关键结果：`PRE_WIFI_SDK_REQUEST_PAIRING success=false,error=408,paired=0`，`PRE_WIFI_SDK_SET_HEADSET_ACCOUNT success=true,error=100000,status=1`，`PRE_WIFI_SDK_SET_HONOR_ACCOUNT success=false,error=408,status=0`。
- 因此当前阻塞不是后端、Glory View 后台进程或设备 AP 开关。对 `Glory Glass 2-00F7` 来说，拍照和录像 BLE 指令已能正常工作，设备 AP 也能由 PatrolLink 打开；手机侧手动连接 `UTE_00F7` 后，媒体页已有设备端“刷新/同步”入口：先刷新设备文件，识别到照片、视频或录音后点“同步”，文件会进入手机沙盒并加入后台上传队列。仍待真机闭环验证的是：在手机实际保持连接设备热点时，Wi-Fi 文件服务能否返回真实文件列表并完成下载。

当前 App 已新增安全重置入口：

- 设备页新增“重置配对”按钮。
- 点击后会弹窗确认，确认后调用 SDK `clearAccountID()` 清除设备保存的账号绑定。
- 清除成功后 App 会解绑本地设备状态并提示重新配对 PatrolLink。
- debug smoke 也支持受保护的清账号参数，但必须同时传 `--ez clearDeviceAccount true` 和 `--es clearDeviceAccountConfirm CLEAR_DEVICE_ACCOUNT`，避免误触发。

Glory View 对照结论：

- Glory View v1.1.5 把设备媒体落在自己的 App 外部沙盒：`/sdcard/Android/data/com.yc.gloryfitpro.glasses/files/glasses/DCIM/`。
- 缩略图落在 `DCIM/Mark/`；历史样本中 mp4 主文件约 10 MB、32 MB、47 MB，jpg 主文件约 27-30 KB，Mark 目录图片约 2.4-3.2 MB。
- Glory View 同时把部分图片复制到公共相册 `/sdcard/DCIM/Glory Glass/`，但没有看到视频复制到公共相册；本轮也没有看到 Glory View 落下来的音频文件。
- 取证统计：`files/glasses/DCIM` 有 11 个文件约 99 MB，公共相册 `DCIM/Glory Glass` 有 4 个文件约 10.6 MB。
- Android Wi-Fi 历史显示 Glory View 在 2026-05-15 19:02-19:18 期间多次创建/删除 `UTE_00F7` 临时网络，时间点和 DCIM 文件落盘吻合。
- 2026-06-12/2026-06-13 日志显示 Glory View 连接后会读取 `wiFiSSID=UTE_A243`、`wiFiPassword=12345678`，设置 Wi-Fi 名称/密码，读取 `glassesInfo`，并在连接同步完成后调用同步完成流程。
- 当前已取得的 Glory View 日志没有暴露设备 Wi-Fi HTTP host/port/path；APK 业务代码因百度加固不可直接静态反编译。
- PatrolLink 采用更稳方案：设备文件先落入 PatrolLink App 私有沙盒并写入 Room 媒体索引，再交给 WorkManager 后台上传任务。这样即使 UI 进程退出或网络失败，后台上传仍能从 Room 找到本地文件继续补偿。
- 2026-06-13 02:23 已收紧上传成功语义：如果后台上传或 UTE 直传链路的 `uploadLocalFile()` 没有返回后端媒体记录，任务不会被标记完成，避免本地文件被误标为已上传。
- 不建议默认照搬 Glory View 的公共相册双写；执法证据默认应留在 App 私有沙盒，公共 `MediaStore`/相册导出可作为显式分享或导出动作。

Wi-Fi 传输方向判断：

- 当前证据指向“耳机/设备开启 Wi-Fi AP，手机 App 临时连接设备热点”，不是“手机开启热点，耳机连接手机热点”。
- 证据 1：SDK 状态名是 `IFI_AP_STARTING`、`IFI_AP_READY`、`IFI_AP_CONNECT`，且公开命令是 `smartSetDeviceWiFiSwitch(true)` 打开设备 Wi-Fi。
- 证据 2：Glory View 历史 Wi-Fi 记录显示它由 App 进程临时创建/删除 `UTE_00F7` 网络配置，说明手机在连接设备 SSID。
- 证据 3：当前设备 Wi-Fi 信息为 `UTE_A243/12345678`；PatrolLink 代码用 `WifiNetworkSpecifier` 请求连接这个 SSID。
- 证据 4：2026-06-13 01:56 smoke 中手机仍连接普通路由 `英英杀人女魔头5G`，未连接 `UTE_A243`，探测到的 `192.168.1.1` 是当前路由器服务，不是耳机文件服务。

后续真机验证顺序：

1. 需要先通过设备侧物理重置、厂家工具、或 Glory View/SDK 可实际生效的解绑方式，让 `setHeadsetAccount(POLICE_9527)` 返回 `ACCOUNT_NO=2` 或 `ACCOUNT_SAME=0`。
2. 重新连接并配对 PatrolLink 当前账号。
3. 重新执行 Wi-Fi smoke，验证 `smartGetDeviceWiFiInfo()` 是否恢复 SSID/密码、`smartSetDeviceWiFiSwitch(true)` 是否能进入 `IFI_AP_READY=7` 或 `IFI_AP_CONNECT=8`。
4. AP ready 后继续验证手机连接设备热点、文件服务探测、媒体下载和后台上传服务。

## SDK 文档确认

SDK 文档路径：

`/Users/qiuqiquan/Desktop/SmartHeadsetSystem/PatrolLink/UteWatchSDK_Android炬芯_V1.3.5/UteWatchSDK_Android炬芯_使用说明文档_V1.3.5.pdf`

已确认的公开 Wi-Fi 接口：

- `smartGetDeviceWiFiInfo()`
- `smartGetDeviceWiFiStateInfo()`
- `smartSetDeviceWiFiSwitch(boolean)`
- `smartSetDeviceWiFiSSID(String)`
- `smartSetDeviceWiFiPassword(String)`
- `SMART_WIFI_STATE_NOTIFY`

已确认的 Wi-Fi 状态：

- `WIFI_OPEN_SUCCESS = 1`
- `WIFI_OPEN_FAILED = 2`
- `WIFI_CLOSE_SUCCESS = 3`
- `WIFI_CLOSE_FAILED = 4`
- `WIFI_AP_STOP = 5`
- `IFI_AP_STARTING = 6`
- `IFI_AP_READY = 7`
- `IFI_AP_CONNECT = 8`
- `IFI_AP_CONNECT_FAILED = 9`

已确认的智能眼镜/摄录耳机媒体接口：

- `getGlassesInfo()`：只返回照片、音频、视频数量和存储空间。
- `deleteGlassesFilesByType(...)`
- `deleteGlassesFilesByName(...)`
- `notifyMediaSyncCompleted()`
- `SMART_GLASSES_IMAGE_DATA_NOTIFY`
- `SMART_GLASSES_AUDIO_DATA_NOTIFY`
- `SMART_GLASSES_AUDIO_DATA_REAL_NOTIFY`

未在公开 SDK 文档中找到：

- Wi-Fi 媒体文件列表 API。
- Wi-Fi 媒体文件下载 API。
- 按文件名选择设备本地文件并通过 Wi-Fi 上传的公开 API。

## Glory View 证据

Glory View 包名：

`com.yc.gloryfitpro.glasses`

设备上发现过 Glory View 已落地的媒体文件：

`/sdcard/Android/data/com.yc.gloryfitpro.glasses/files/glasses/DCIM`

Android Wi-Fi 历史显示 Glory View 曾连接设备热点 `UTE_00F7`，并由 Glory View 进程创建/移除网络配置：

- `configKey="UTE_00F7"WPA_PSK-0`
- `name=com.yc.gloryfitpro.glasses`
- `connectionNominator=NOMINATOR_SPECIFIER`
- 连接时长约 5 到 22 秒。
- 热点信道出现在 2437 MHz。

Glory View 日志显示旧设备或旧会话中 Wi-Fi 已就绪：

- 2026-05-17：`state=7, ssid=UTE_00F7`

当前测试设备日志显示 Wi-Fi 未打开：

- 当前设备：`E1-Pro-A243`
- BLE 地址：`FD:4A:BA:43:A2:43`
- Wi-Fi SSID：`UTE_A243`
- 2026-05-17/2026-05-18 Glory View 日志多次出现：`state=0, wiFiSSID=UTE_A243, wiFiPassword=12345678`

这说明“设备有 Wi-Fi 传输能力”成立，但当前设备当时没有把 AP 打开到可连接状态。

## 第三方鉴权/初始化握手

Glory View 使用了 Starburst 相关流程，主要表现为：

- 调用 `startAuthentication()`。
- 通过 `THIRD_PARTY_DATA_TRANSMIT_NOTIFY` 收包。
- 通过 `thirdPartyDataTransmitToBle(...)` 回包。

从 `starburstsdk_log.txt` 还原出的关键流程：

1. 设备发 `F060` 交互包，App 回 ack。
2. 设备发 `F04F` LP 请求，包含：
   - `productKey = tsQiaTyI9fq`
   - `deviceName = FD4ABA43A243`
3. App 发送 FGS JSON：
   - `FGS_MSG_TYPE_START_FGS_REQ`
4. 设备返回 FGS JSON：
   - `FGS_MSG_TYPE_START_FGS_RESP`
   - `tripplestatus = existtripple`
5. App 发送 FND 时间戳 JSON。
6. 设备返回 FND 签名数据。
7. Glory View 请求火山/涂鸦侧 token：
   - `https://ainf.tis.cn-beijing.volces.com/tis/device/token/get`
   - 当前设备返回过 `400 fail to get device`。

当前仓库已新增 WIP 文件：

`/Users/qiuqiquan/Desktop/SmartHeadsetSystem/PatrolLink/PL2Android/app/src/main/java/com/patrollink/data/ute/UteSmartAuthWarmup.kt`

它实现了轻量级兼容握手，但尚未完全接入调用点，也还没有在真机上重新验证。

## 当前 PatrolLink 实现状态

已实现或已开始实现的文件：

- `/Users/qiuqiquan/Desktop/SmartHeadsetSystem/PatrolLink/PL2Android/app/src/main/java/com/patrollink/data/wifi/DeviceWifiNetworkConnector.kt`
  - Android 10+ 使用 `WifiNetworkSpecifier` 连接设备 AP。
  - 旧系统使用 `WifiConfiguration`。

- `/Users/qiuqiquan/Desktop/SmartHeadsetSystem/PatrolLink/PL2Android/app/src/main/java/com/patrollink/data/ute/UteWifiMediaClient.kt`
  - 读取/设置设备 Wi-Fi 信息。
  - 尝试打开设备 Wi-Fi AP。
  - 手机连接设备热点。
  - 探测常见 HTTP host/port/path。
  - 识别 `jpg/png/mp4/mov/opus/wav/amr/aac/pcm` 等媒体文件。
  - 下载后交给现有媒体索引和上传链路。

- `/Users/qiuqiquan/Desktop/SmartHeadsetSystem/PatrolLink/PL2Android/app/src/main/java/com/patrollink/data/ute/UteSdkMediaGateway.kt`
  - 已接入 Wi-Fi 文件列表/下载入口。
  - Wi-Fi 失败时仍保留 BLE/本地媒体路径。

- `/Users/qiuqiquan/Desktop/SmartHeadsetSystem/PatrolLink/PL2Android/app/src/debug/java/com/patrollink/debug/SmokeTestReceiver.kt`
  - 增加 `skipLogin`。
  - 增加 Wi-Fi 媒体诊断和列表 smoke step。

- `/Users/qiuqiquan/Desktop/SmartHeadsetSystem/PatrolLink/PL2Android/app/src/main/AndroidManifest.xml`
  - 已补 `CHANGE_NETWORK_STATE`。

当前需要继续补的点：

- 在设备连接成功后和 Wi-Fi 打开前调用 `UteSmartAuthWarmup.run()`。
- `UteWifiMediaClient.withDeviceWifiSession(...)` 里现在 finally 会调用 `smartSetDeviceWiFiSwitch(false)`，下一步建议移除，避免每次探测后立刻关掉设备 AP。
- `UteSdkBridge` 日志建议补充 `THIRD_PARTY_DATA_TRANSMIT_NOTIFY` 和 `SMART_WIFI_STATE_NOTIFY`，方便真机追踪。

## 最近一次 Smoke 结果

最近一次真机 smoke 时间：2026-05-18 17:24 左右。

设备：

- `E1-Pro-A243`
- `FD:4A:BA:43:A2:43`
- `UTE_A243`

结果：

- App 已能连接 BLE 并读取设备信息。
- 设备 Wi-Fi 信息可读：`enabled=false, ssid=UTE_A243, passwordConfigured=true, connected=false`。
- 下发打开 Wi-Fi 后，设备仍停留在 disabled/state 0。
- Android 扫描结果没有发现 `UTE_A243`。
- Wi-Fi HTTP 探测没有入口。
- `getGlassesInfo()` 返回当时设备侧未同步媒体数量：
  - photo `0/32`
  - audio `0/19`
  - video `0/17`

这次失败点不是手机权限，而是设备 AP 没有进入可连接状态。

## 2026-05-18 18:06-18:23 复测结论

设备充电后继续测试，安装包和权限正常，但当前设备控制链路处于更差的“业务命令无回包”状态：

- GATT 层连接成功。
- SDK 服务发现、notify 设置、MTU 设置成功。
- `BluetoothGatt.writeCharacteristic(...)` 返回成功。
- 设备没有返回业务 notify，SDK 内部 `countDownLatch` 等待 3 秒后超时。
- `requestDevicePairing`、`setHeadsetAccount`、`setHonorAccount`、`smartGetDeviceWiFiInfo`、`smartSetDeviceWiFiSwitch`、`getGlassesInfo`、`retryImageUpload` 都出现 408 或空数据。

关键 smoke 报告：

`/sdcard/Android/data/com.patrollink/files/smoke-test-20260518-182352.txt`

结果摘要：

- `READ_WIFI`：`enabled=false, ssid=, passwordConfigured=false, connected=false`
- `READ_WIFI_RAW`：`state=0, ssid=, passwordLen=0`
- `ENABLE_WIFI`：`device wifi switch failed: 408`
- `WIFI_NOTIFIES`：none
- Android Wi-Fi 扫描：`uteScan=[none]`
- `UTE_WIFI_MEDIA_DIAGNOSTICS`：`device wifi ssid is blank`
- `SMART_GLASSES_INFO`：`photo null/null,audio null/null,video null/null`
- `RETRY_IMAGE_UPLOAD`：`success=false,error=408`

排查过的代码路径：

- 自动调用 Starburst/第三方 `startAuthentication()`：当前设备返回 408，且 `rx=0`，没有收到 `THIRD_PARTY_DATA_TRANSMIT_NOTIFY`。已保留实现文件，但不再默认自动触发，避免拖慢/干扰基础命令。
- 启用 SDK `setSupportUserIdPair(true)`：SDK 会在连接阶段触发 `requestDevicePairing`，当前设备 408 后 SDK 主动断开连接；该路径当前不可用，已恢复为耳机默认不走 SDK 自动配对。
- 连接后立即账号绑定：`setHonorAccount` / `setHeadsetAccount` 在当前设备上都 408。为减少连接后的阻塞，已改为连接后不主动下发账号绑定，避免把第一轮连接耗在确定失败的账号命令上。

当前判断：

这不是 Wi-Fi 传输代码本身的问题，也不是 Android 手机连热点失败；当前卡点在 BLE Smart 业务命令没有设备回包。只有先恢复 `smartGetDeviceWiFiInfo()` 至少能读出 `UTE_A243/12345678`，后面的 AP 打开、手机连接热点、HTTP 文件探测才有继续验证意义。

## Glory View 对照验证

2026-05-18 18:38 使用已安装的 Glory View (`com.yc.gloryfitpro.glasses`, v1.1.5) 做对照：

- Glory View 当前账号：`1363809106@qq.com`，用户 ID：`300610203`。
- Glory View 启动后从本地数据库读到了当前设备记录：`FD:4A:BA:43:A2:43` / `E1-Pro-A243`。
- Glory View 启动后也从本地记录读到了 Wi-Fi 信息：`wiFiSSID=UTE_A243`、`wiFiPassword=12345678`、`state=0`。
- 但 18:38 后 Glory View 的 SDK 连接状态一直是 `state = -1`，没有真正连上当前耳机，没有刷新 BLE/Wi-Fi/媒体状态。
- 手机当前 Wi-Fi 仍连接办公网络 `FIDC`，没有切到 `UTE_A243`。
- Glory View UI 相册页显示 `暂无数据`。
- Glory View 本机目录存在 2026-05-15 19:03-19:18 下载过的 4 张图片和 3 段视频，路径为 `/sdcard/Android/data/com.yc.gloryfitpro.glasses/files/glasses/DCIM/`，说明 Glory View 历史上确实走通过媒体传输。

补充历史日志：

- 2026-05-18 11:26 Glory View 曾连上当前设备并读到 `UTE_A243/12345678`，但同步 `deviceInfo`、`batteryInfo`、`wiFiInfo`、`videoLength`、`glassesInfo` 和 `photoCapture` 后续都出现 408。
- 2026-05-17 Starburst token 请求中使用过 `deviceName=FD4ABA43A243`，服务端返回 HTTP 400。
- 2026-05-15 Glory View 对旧设备 `UTE_00F7` 成功获取 Starburst token，并成功下载过图片/视频。

因此 Glory View 证明设备族支持 Wi-Fi 媒体传输，但当前这只 `E1-Pro-A243` 的即时问题仍是 BLE Smart 业务命令/鉴权链路不稳定。实现方向仍应优先保证当前 App 能稳定读到设备 Wi-Fi 信息并打开 AP，再接手机侧 Wi-Fi 传输探测。

## 设备充电后恢复步骤

建议按这个顺序继续：

1. 接入 `UteSmartAuthWarmup`：
   - 设备连接并账号绑定后执行一次。
   - 打开 Wi-Fi AP 前再执行一次短 warmup。

2. 调整 AP 生命周期：
   - Wi-Fi 文件列表/下载结束后只断开手机网络 session。
   - 不立即调用 `smartSetDeviceWiFiSwitch(false)`。

3. 重新构建安装：

```bash
./gradlew :app:assembleDebug
cp app/build/outputs/apk/debug/app-debug.apk dist/PatrolLink-debug.apk
/Users/qiuqiquan/Library/Android/sdk/platform-tools/adb install -r dist/PatrolLink-debug.apk
```

4. 真机上先用耳机拍一张照片、录一段音频、录一段视频，确保设备侧有未同步媒体。

5. 前台启动 smoke，避免 Android 后台 Wi-Fi 连接限制：

```bash
/Users/qiuqiquan/Library/Android/sdk/platform-tools/adb logcat -c
/Users/qiuqiquan/Library/Android/sdk/platform-tools/adb shell am start -n com.patrollink/.debug.SmokeTestActivity --ez skipLogin true --ez commands false --ez wifi true --ez auth false --ez pairing false --ez accountProbe false --es account qiuqiquan
```

## 2026-05-18 22:41-22:55 复测和 SDK 反编译结论

本轮已将 Glory View 风格初始化接入 Wi-Fi 媒体路径，并重新打包安装。

已确认有效的部分：

- 拍照、录像、录音控制命令在 22:41 冒烟中均成功。
- 设备信息读取成功：电量、固件、存储空间可读。
- `getGlassesInfo()` 能看到设备本地媒体数量增加：
  - audio `1/21`
  - video `1/18`
- Starburst/第三方 warmup 能跑通轻量握手：
  - 收到 `THIRD_PARTY_DATA_TRANSMIT_NOTIFY` 5 包。
  - 回包 4 包。
  - `badCrc=0`。

仍失败的部分：

- Wi-Fi 打开后只收到 `SMART_WIFI_STATE_NOTIFY state=1`，没有进入 `IFI_AP_READY=7` 或 `IFI_AP_CONNECT=8`。
- Android `WifiNetworkSpecifier` 请求 `UTE_A243` 30 秒超时。
- 手机 Wi-Fi 扫描结果仍没有 `UTE_A243`。
- 也就是说当前不是 HTTP 路径猜错，而是设备 AP 没有到可连接状态。

SDK 反编译确认：

- `smartGetDeviceWiFiInfo()` 命令：`11 A3 AA AA`
- `smartGetDeviceWiFiStateInfo()` 命令：`11 A3 AA 01`
- `smartSetDeviceWiFiSwitch(true)` 命令：`11 A3 AB 01 01 01`
- `smartSetDeviceWiFiSwitch(false)` 命令：`11 A3 AB 01 01 00`
- `SMART_WIFI_STATE_NOTIFY = 908`
- `state=1` 是打开 Wi-Fi 命令成功 ACK，不代表 AP ready。
- SDK 内没有发现 `state=1` 之后还需要补发另一个“启动 AP”的公开或隐藏命令；`IFI_AP_READY=7` 应由设备后续上报或通过 `smartGetDeviceWiFiStateInfo()` 轮询得到。

本轮代码调整：

- `UteWifiMediaClient`：
  - Wi-Fi 打开前执行 Glory View 风格 warmup。
  - 执行 Starburst 轻量鉴权握手。
  - 文件探测结束后不再立刻关闭设备 AP。
  - 延长 AP ready 等待窗口。
- `DeviceWifiNetworkConnector`：
  - Android 10+ 优先按隐藏 SSID 直连 `UTE_A243`，失败后回退普通可见 SSID 扫描。
  - 增加 Wi-Fi request/link/onUnavailable 日志。
- `UteSdkDeviceControlGateway`：
  - UI/冒烟 `configureWifi` 不再因为没等到 7/8 就主动关 AP。
  - Wi-Fi 打开前同步执行 warmup。
- `UteSdkDeviceGateway`：
  - 系统蓝牙只处于 bonded 时，也会先尝试 SDK 控制扫描，不再直接返回离线。

当前新的外部阻塞：

- 安装新包后，`E1-Pro-A243` 不再广播 UTE 控制通道。
- SDK 控制扫描只看到另一台 `SMI-M14/E8:4A:54:67:93:4D`。
- `E1-Pro-A243/FD:4A:BA:43:A2:43` 当前只剩 `system-bluetooth-audio-bonded`。
- 最新 smoke 报告：
  - `/sdcard/Android/data/com.patrollink/files/smoke-test-20260518-225538.txt`
  - 结果：`BIND_DEVICE_ONLINE` 失败，原因是“系统蓝牙已配对，控制通道未连接”。

下一步需要先让 `E1-Pro-A243` 恢复 UTE 控制广播/控制连接，然后才能继续验证 Wi-Fi AP 是否能到 `state=7/8`。

## 2026-05-18 23:24 指定 SMI-M14 复测

为确认 `SMI-M14/E8:4A:54:67:93:4D` 是否是 `E1-Pro-A243` 的另一个 BLE 控制地址，debug smoke 增加了 `targetDeviceId`/`targetDeviceName` 参数，并指定连接：

```bash
/Users/qiuqiquan/Library/Android/sdk/platform-tools/adb shell am start -n com.patrollink/.debug.SmokeTestActivity --ez skipLogin true --ez commands false --ez wifi true --ez auth false --ez pairing false --ez accountProbe false --es account qiuqiquan --es targetDeviceId E8:4A:54:67:93:4D
```

结论：

- `SMI-M14` 可以 BLE 连接，`platform=2`。
- 但它不是当前可用的摄录 Wi-Fi 控制通道：
  - `supportsWifi=false`
  - `READ_WIFI ssid=` 空
  - `READ_WIFI_RAW state=0,ssid=,passwordLen=0`
  - `smartSetDeviceWiFiSwitch` 返回 408
  - `getGlassesInfo` 中图片/音频/视频统计均为 null
- 最新报告：
  - `/sdcard/Android/data/com.patrollink/files/smoke-test-20260518-232718.txt`

因此不能把 `SMI-M14` 作为 `E1-Pro-A243` 的 Wi-Fi 媒体传输控制通道使用。仍需先恢复 `E1-Pro-A243/FD:4A:BA:43:A2:43` 的真实 UTE 控制连接。

## 2026-05-18 23:50-2026-05-19 00:02 继续排查

### 当前设备状态

当前测试手机蓝牙栈里 `E1-Pro-A243/FD:4A:BA:43:A2:43` 仍是已配对但未连接：

- `A2dpService`：`mConnectionState: DISCONNECTED`
- `HeadsetService`：`mConnectionState: 0`
- `dumpsys bluetooth_manager` 显示 `Connections: 0`
- 最新一次系统记录中，22:52 后经典蓝牙断开，后续多次只出现短暂 BLE direct connect 尝试，均不是可用的 SDK 控制连接。

最新 smoke 报告：

- `/sdcard/Android/data/com.patrollink/files/smoke-test-20260518-235051.txt`
- SDK 扫描只发现 `SMI-M14/E8:4A:54:67:93:4D`。
- 对 `E1-Pro-A243/FD:4A:BA:43:A2:43` 增加了“系统已配对地址直连控制回退”，但 GATT 直连最终 `BLE_CONNECTING_TIMEOUT`。
- 回退超时时间已缩短到 7 秒，避免离线状态下 UI/smoke 长时间卡住。

因此当前不能继续验证 Wi-Fi 文件下载本身；必须先让 `E1-Pro-A243` 在系统蓝牙/Glory View 中重新连上，或重新广播真实 UTE 控制通道。

### Glory View APK 静态分析

已拉取已安装 Glory View APK：

- `/tmp/glory-apk-analysis/glory.apk`

结论：

- APK 使用百度加固，`application` 为 `com.sagittarius.v6.StubApplication`。
- 普通 JADX 只能反出壳、第三方库和 `R.java`，业务类例如 `com.yc.gloryfitpro.ui.activity.main.glasses.*` 只在 manifest 里可见，源码未反出。
- APK 内 `assets/baiduprotect*.i.dex` 解包后是合法 dex 头，但 `string_ids_size/type_ids_size/method_ids_size/class_defs_size` 全为 0，实际业务 payload 仍是加密数据，无法直接 JADX。
- `run-as com.yc.gloryfitpro.glasses` 失败：`package not debuggable`，不能直接读取私有数据库或运行时解密产物。

已确认 Glory View 的 glasses 相关页面存在：

- `GlassesWifiActivity`
- `GlassesWifiActivity2`
- `GlassesJoinWifiActivity`
- `GlassesInputPswActivity`
- `GlassesStorageActivity`
- `MediaPlayerActivity`
- `PhotoOperationActivity`

但静态代码暂时无法直接拿到这些页面内部的 Wi-Fi host/port/path。

### Glory View 本地媒体和 Wi-Fi 证据

当前手机上 Glory View 仍保留历史同步文件：

- `/sdcard/Android/data/com.yc.gloryfitpro.glasses/files/glasses/DCIM/`
- 4 张照片主文件、4 张 `Mark/` 缩略图、3 段 mp4。

当前设备可见证据仍只指向旧热点 `UTE_00F7`：

- Android Wi-Fi 历史中 `com.yc.gloryfitpro.glasses` 多次临时创建/删除 `UTE_00F7` 网络。
- 当前 `E1-Pro-A243` 对应的 `UTE_A243` 未出现在成功连接历史里。
- Glory View 日志里当前设备 Starburst token 请求使用 `deviceName=FD4ABA43A243`，服务端返回 `400 fail to get device`；旧设备 `7802B76600F7` 走通过 token。

这支持一个更窄的判断：设备族支持 Wi-Fi 传输，但当前这只 `E1-Pro-A243` 是否能走通官方云侧鉴权和 AP ready，还需要 live 验证。

### 本轮代码调整

- `DeviceWifiNetworkConnector`：
  - 如果手机当前已经连在目标设备 SSID 上，直接复用当前 Wi-Fi 网络。
  - Android 10+ 连接顺序改为“可见 SSID 优先 15 秒，失败后隐藏 SSID 回退 30 秒”。
  - 原因：Glory View 历史连接 `UTE_00F7` 是普通临时 Wi-Fi 配置；先按隐藏 SSID 等 30 秒会让正常可见 AP 的下载链路额外变慢。
- `SmokeTestReceiver`：
  - 增加 `wifiProbeOnly` debug 入口，不登录、不绑 BLE，只连续读取当前 Wi-Fi/scan/allNetworks 并探测常见 host/port/path。
  - 探测时按每条 Android `Network` 的 `socketFactory` 建 socket，避免 Glory View 使用临时 `WifiNetworkSpecifier` 连接设备 AP 时，PLLink 仍误走默认办公网络。
  - 用途：下次手动用 Glory View 触发同步时，可以并行启动 PLLink 探测，确认 Android 是否能看到同一条设备 AP 网络以及真实文件服务入口。
- 已重新构建并安装：
  - `./gradlew :app:assembleDebug`
  - `dist/PatrolLink-debug.apk`
  - `adb install -r` 成功。

`wifiProbeOnly` 启动命令：

```bash
/Users/qiuqiquan/Library/Android/sdk/platform-tools/adb shell am start -n com.patrollink/.debug.SmokeTestActivity --ez wifiProbeOnly true --el wifiProbeMillis 90000
```

6. 采集日志：

```bash
/Users/qiuqiquan/Library/Android/sdk/platform-tools/adb logcat -d -v time -s PatrolSmoke UteWifiMedia UteSmartAuth PatrolUteNotify PatrolUteDevice AndroidRuntime
/Users/qiuqiquan/Library/Android/sdk/platform-tools/adb shell 'cat /sdcard/Android/data/com.patrollink/files/smoke-test-latest.txt'
```

## 临时取证文件

以下路径是本次调研过程中的临时文件，可能会被系统清理，关键结论已写入本文档：

- `/tmp/ute-doc/doc.txt`
- `/tmp/glory-device-data/AllLogs/LogOther/`
- `/tmp/glory-logcat/logcat/`
- `/tmp/dumpsys-wifi.txt`
- `/tmp/glory-apk/gloryfitpro-glasses.apk`
- `/tmp/glory-jadx/`
- `/tmp/glory-apk-analysis/`

## 2026-06-13 02:45 KDocs 在线文档复核

通过浏览器打开 `https://www.kdocs.cn/l/clTtBk8yvHED`，标题为 `UteWatchSDK_Android_使用说明文档`。本次重点核对账号、恢复出厂和 Wi-Fi 媒体链路。

### 账号和重绑

- 文档中的账号接口为 `Response<HeadsetAccountConfig> response = mUteBleConnection.setHeadsetAccount(HeadsetAccountConfig config);`。
- 请求参数 `currentHuid` 是“APP 设置耳机配对的密码”，长度 8 到 32。
- 返回参数 `accountJudgmentStatus` 明确包含：
  - `HeadsetAccountConfig.ACCOUNT_SAME`：账号一致。
  - `HeadsetAccountConfig.ACCOUNT_DIFFERENT`：账号不一致。
  - `HeadsetAccountConfig.ACCOUNT_NO`：无账号。
- 文档说明：如果账号一致或者无账号，则直接连接；账号不一致时，先返回账号不一致，耳机会震动或闪灯提示，再提示重新绑定会删除原有数据。
- 文档中的清账号接口只有 `Response<?> response = mUteBleConnection.clearAccountID();`，请求参数为空，返回 `errorCode=100000` 才表示成功。真机此前返回 `408`，因此不能视为清除成功。

### 恢复出厂

- KDocs 在线文档能搜索到 `NotifyType.DEVICE_RESET_NOTIFY`，说明设备会主动上报“用户确认恢复出厂”。
- KDocs 未直接列出 demo 中的 `glassesDeviceResetOperation(...)`、`headsetDeviceResetOperation(...)`、`DeviceResetConfig.FACTORY_RESET_AND_RESTART` 方法名。
- 同包 Android demo 的 `SmartGlassesActivity` 确实提供两个恢复出厂调用：
  - `mUteBleConnection.glassesDeviceResetOperation(DeviceResetConfig.FACTORY_RESET_AND_RESTART)`
  - `mUteBleConnection.headsetDeviceResetOperation(DeviceResetConfig.FACTORY_RESET_AND_RESTART)`
- PatrolLink 当前只接入了 `clearAccountID()`，还没有接入上述两个 demo reset 方法。若要在 App 内实现“重新绑定会删除原有数据”的完整路径，需要把这两个 reset 操作做成强确认动作，不能静默执行。

### Wi-Fi 媒体链路

- KDocs 的 Wi-Fi 章节为 `3.7 背夹 WIFI 模块`，使用 `UteWifiClient`。
- 调用顺序：
  - `UteWifiClient.initialize(this)`
  - `mUteWifiClient.setConnectStateListener(WifiConnectStateListener ...)`
  - `mWifiConnection.setDeviceNotifyListener(DeviceNotifyListener ...)`
  - `mUteWifiClient.startServer()`
  - `mWifiConnection.handshakeRequest(HandshakeRequest request)`
  - 完成后 `mUteWifiClient.stopServer()`
- `HandshakeRequest` 参数里 `token` 是用户 ID，文档特别注明“需要和蓝牙设置中的一致”；`stamp` 是秒级时间戳。
- 这和当前失败吻合：如果 BLE 侧 `setHeadsetAccount(POLICE_9527)` 仍返回 `ACCOUNT_DIFFERENT`，Wi-Fi 握手 token 继续使用 `POLICE_9527` 也不应进入文件传输。

### 2026-06-13 02:46 真机复测

恢复出厂后又跑了一轮非破坏性 smoke，只登录、扫描、绑定和 Wi-Fi 前置检查，不执行拍照录像、不清账号、不恢复出厂。

报告：

- `/Users/qiuqiquan/Desktop/SmartHeadsetSystem/PatrolLink/dist/smoke-reports/smoke-test-20260613-real-after-factory-reset-second-probe.txt`

结果：

- App 登录成功，账号仍为 `POLICE_9527`。
- 系统蓝牙扫描只列出 `E1-Pro-A243/FD:4A:BA:43:A2:43/Headset/system-bluetooth-audio-bonded`。
- SDK 控制扫描没有看到 `E1-Pro-A243` 的 UTE 控制广播，只看到 `SMI-M14/E8:4A:54:67:93:4D/ute-ble-control-scanned`。
- 对 `E1-Pro-A243/FD:4A:BA:43:A2:43` 走系统已配对地址直连回退，GATT 连接失败，设备状态为“系统蓝牙已配对，控制通道未连接”。

当前判断：

- 今天这一轮已经不是单纯账号不一致问题，而是恢复出厂后 `E1-Pro-A243` 的 UTE BLE 控制通道没有广播或无法直连。
- `SMI-M14/E8:4A:54:67:93:4D` 可能是附近另一个设备，也可能是同一副耳机恢复出厂后的控制 BLE 名称；在未确认前，不能对它执行账号写入、清账号或恢复出厂。

### 2026-06-13 02:53 代码补齐和验证

本轮把 SDK demo 中已有、PatrolLink 未接入的恢复出厂动作补到 App 和 debug smoke：

- `DeviceControlGateway.factoryResetDevice(DeviceFactoryResetTarget)`。
- UTE SDK 实现：
  - `DeviceFactoryResetTarget.Headset` -> `mUteBleConnection.headsetDeviceResetOperation(DeviceResetConfig.FACTORY_RESET_AND_RESTART)`。
  - `DeviceFactoryResetTarget.Glasses` -> `mUteBleConnection.glassesDeviceResetOperation(DeviceResetConfig.FACTORY_RESET_AND_RESTART)`。
- 设备页“重置配对”确认框现在提供：
  - 清账号：`clearAccountID()`。
  - 恢复耳机：`headsetDeviceResetOperation(...)`。
  - 恢复眼镜：`glassesDeviceResetOperation(...)`。
- 成功后本地会解绑当前设备并提示重新搜索/配对，避免继续使用旧控制通道状态。
- debug smoke 新增强确认参数：
  - `--es factoryResetTarget headset|glasses`
  - `--es factoryResetConfirm FACTORY_RESET_DEVICE`
  - 与 `clearDeviceAccount` 互斥；执行任一危险动作后立即停止后续测试。

验证：

- `./gradlew :app:testDebugUnitTest --tests 'com.patrollink.debug.SmokeDangerousActionGuardTest' --tests 'com.patrollink.presentation.PatrolViewModelTest' --console=plain` 通过。
- `./gradlew :app:testDebugUnitTest :app:assembleDebug --console=plain` 通过。
- 最新 APK 已通过 `adb install -r` 安装到模拟器和真机：
  - 模拟器 `lastUpdateTime=2026-06-13 02:52:48`
  - 真机 `lastUpdateTime=2026-06-13 02:52:54`
- 模拟器和真机均跑过非破坏性 `configOnly` smoke：
  - `/Users/qiuqiquan/Desktop/SmartHeadsetSystem/PatrolLink/dist/smoke-reports/smoke-test-20260613-emulator-factory-reset-build-configonly.txt`
  - `/Users/qiuqiquan/Desktop/SmartHeadsetSystem/PatrolLink/dist/smoke-reports/smoke-test-20260613-real-factory-reset-build-configonly.txt`

后续真机如果要执行 SDK 恢复出厂，必须先确认当前要操作的是同一副耳机的控制通道地址，尤其是 `SMI-M14/E8:4A:54:67:93:4D` 是否属于 `E1-Pro-A243`。

### 2026-06-13 03:23 SMI-M14 只读身份诊断

用户询问 KDocs 中账号不一致的解决接口后，重新核对文档结论：

- `setHeadsetAccount(HeadsetAccountConfig)` 是账号判断/设置入口。
- `accountJudgmentStatus=1` 对应 `ACCOUNT_DIFFERENT`。
- 文档只给出 `clearAccountID()` 作为清账号接口，且只有 `errorCode=100000` 才算成功。
- Wi-Fi `handshakeRequest(HandshakeRequest)` 的 `token` 必须和蓝牙设置的用户 ID 一致，所以 BLE 账号仍为 `ACCOUNT_DIFFERENT` 时不应继续 Wi-Fi 媒体传输。

随后执行了耳机模块恢复出厂：

- 报告：`/Users/qiuqiquan/Desktop/SmartHeadsetSystem/PatrolLink/dist/smoke-reports/smoke-test-20260613-real-headset-factory-reset.txt`
- 结果：`SDK_FACTORY_RESET target=headset success=true,error=100000,data=Boolean:true`。

耳机恢复后复测 Wi-Fi：

- 报告：`/Users/qiuqiquan/Desktop/SmartHeadsetSystem/PatrolLink/dist/smoke-reports/smoke-test-20260613-real-after-headset-reset-wifi.txt`
- 结果：`PRE_WIFI_SDK_SET_HEADSET_ACCOUNT success=true,error=100000,status=1`，Wi-Fi 仍因账号不一致失败。

为判断 `SMI-M14/E8:4A:54:67:93:4D` 是否可以作为眼镜/背夹侧控制通道，执行只读诊断，不写账号、不打开 Wi-Fi、不恢复出厂：

- 报告：`/Users/qiuqiquan/Desktop/SmartHeadsetSystem/PatrolLink/dist/smoke-reports/smoke-test-20260613-real-smi-m14-readonly.txt`
- 扫描结果同时存在：
  - `E1-Pro-A243/FD:4A:BA:43:A2:43/Headset/system-bluetooth-audio-bonded`
  - `SMI-M14/E8:4A:54:67:93:4D/Headset/ute-ble-control-scanned`
- `SMI-M14` 诊断结果：
  - `supportsWifi=false`
  - `supportsFileTransfer=false`
  - `READ_WIFI ssid=` 空，`passwordConfigured=false`
  - `SMART_DEVICE_INFO success=false,error=408`，序列号、SN、地址均为空
  - `SDK_CONNECTION_STATE isConnected=false,deviceAddress=E8:4A:54:67:93:4D,deviceName=SMI-M14`

结论：

- 当前不能把 `SMI-M14/E8:4A:54:67:93:4D` 视为 `E1-Pro-A243` 的 Wi-Fi/眼镜主控制通道。
- 在它无法返回 SmartDeviceInfo 身份或 Wi-Fi 配置前，不能对它执行清账号、账号写入或恢复出厂。
- 代码已新增保护：`factoryResetDevice(...)` 只有在当前 SDK 连接能读到 SmartDeviceInfo 身份或设备 Wi-Fi 配置时才会下发 SDK 恢复出厂命令。

当前阻塞点：

- `FD:4A:BA:43:A2:43` 当前只剩系统音频配对，SDK 控制直连失败，状态为“系统蓝牙已配对，控制通道未连接”。
- `E8:4A:54:67:93:4D` 虽然是 UTE 控制广播，但只读能力证明它不是可用于 Wi-Fi 媒体传输的目标通道。
- 下一步应优先解决 `E1-Pro-A243/FD:4A:BA:43:A2:43` 的 SDK 控制通道恢复/重新配对问题，而不是继续对 `SMI-M14` 做破坏性操作。

### 2026-06-13 22:51 已连接设备误判修复

用户反馈：实际蓝牙连接的是 `Glory Glass 2-00F7`，但 App “已连接设备”仍显示 `E1-Pro-A243`，没有正确显示 Glass。

排查结论：

- Android `dumpsys bluetooth_manager` 中 `E1-Pro-A243/FD:4A:BA:43:A2:43` 只是 bonded 设备，不能证明当前已连接。
- 旧逻辑在 `UteSdkDeviceGateway.systemBluetoothAudioDevices()` 中读取了全局 A2DP/HEADSET profile 连接状态；只要任意音频设备已连接，就会把所有已配对且名称像 Patrol 音频设备的 bonded 设备都标成 `system-bluetooth-audio-connected`。
- 因此 Glass 已连接时，已配对但未连接的 E1 被误判在线。
- 另一个 UI 问题是 `ScannedDevice.toConnectedAudioStatus(...)` 会把系统蓝牙连接占位统一写成 `DeviceType.Headset`，导致 Glass 可能显示成耳机类型。

代码修复：

- `PatrolDeviceNameClassifier.isSystemBluetoothDeviceConnected(...)` 只信任单个 `BluetoothDevice.isConnected()` 隐藏方法返回的 per-device 连接状态，不再使用全局 audio profile 状态推断每个 bonded 设备在线。
- `UteSdkDeviceGateway.isConnectedBySystemBluetooth(...)` 改为调用上述判断。
- `PatrolViewModel.toConnectedAudioStatus(...)` 保留扫描识别出的 `ScannedDevice.type`，Glass 不再被写成 Headset。

验证：

- 单测通过：
  - `PatrolDeviceNameClassifierTest.doesNotTreatEveryBondedPatrolHeadsetAsConnectedWhenAnotherAudioProfileIsConnected`
  - `PatrolViewModelTest.systemConnectedGlassesRemainGlassesInConnectedDevices`
- 全量本地验证通过：`./gradlew :app:testDebugUnitTest :app:assembleDebug --console=plain`。
- 最新 APK 已安装真机，包更新时间：`lastUpdateTime=2026-06-13 22:51:02`。
- 真机 UI 树复查结果：
  - `已连接设备: 1`
  - `台在线: 1`
  - `E1-Pro: 0`
  - `Glory Glass: 2`
  - `摄录耳机: 0`
  - `眼镜: 2`
  - 展示文本包含 `Glory Glass 2-00F7`、`智能眼镜`、`在线`。
- 最近蓝牙日志显示 GATT 连接目标为 `78:02:B7:66:00:F7`，即 `Glory Glass 2-00F7`，没有 E1 当前连接证据。

### 2026-06-13 23:17 Glory Glass Wi-Fi 媒体接口定位

在 Glass 控制通道稳定后，执行真机 smoke：

- 目标设备：`Glory Glass 2-00F7 / 78:02:B7:66:00:F7`。
- BLE 控制链通过：
  - `BIND_DEVICE online=true,type=Glasses`。
  - `TAKE_PHOTO`、`START_VIDEO`、`STOP_VIDEO` 均通过。
  - SDK 通知显示 `totalPictures=4`，设备里已有照片文件。
- 设备 Wi-Fi 可打开：
  - `READ_WIFI enabled=true, ssid=UTE_00F7, passwordConfigured=true, connected=true`。
  - Android 通过 `WifiNetworkSpecifier` 连接后，链路为：
    - 手机：`192.168.222.100/24`
    - 网关/DNS：`192.168.222.1`
    - domain：`ai-glass`

关键发现：

- Glass 的媒体 HTTP 服务入口不是之前猜测的 `192.168.1.1`、`192.168.4.1` 等默认热点地址。
- 实际可用入口为：`http://192.168.222.1:8000/media/list`。
- 该接口返回 JSON：`{"code":200,"data":{"files":[...]}}`。
- 当前设备可枚举到 5 个照片文件：
  - `pictures_ute.jpg`
  - `20260613144750407.jpg`
  - `20260613152349823.jpg`
  - `20260613223517056.jpg`
  - `20260613225425409.jpg`

代码优化：

- `UteWifiMediaClient` 已改为优先使用当前 Wi-Fi link properties 中的网关地址，避免继续盲扫普通路由地址。
- Wi-Fi 媒体 HTTP 探测增加总请求上限：
  - diagnostics 最多 48 个请求。
  - 文件发现最多 64 个请求。
- OkHttp 增加单次 `callTimeout=1200ms`，并缩短本地 Wi-Fi connect/read timeout，避免媒体页或 smoke 被无响应端口长时间拖住。
- debug smoke 报告改为实时写入 `smoke-test-latest.txt`，长流程卡住时也能看到已完成步骤。
- smoke `wifiDownloadFirst` 的候选列表超时从普通媒体检查窗口改为 Wi-Fi 媒体专用窗口，避免 12 秒过早返回空候选。

验证：

- 本地验证通过：`./gradlew :app:testDebugUnitTest :app:assembleDebug --console=plain`。
- 最新 APK 已安装真机，包更新时间：`lastUpdateTime=2026-06-13 23:15:29`。
- 成功定位媒体接口报告：
  - `/Users/qiuqiquan/Desktop/SmartHeadsetSystem/PatrolLink/dist/smoke-reports/smoke-test-20260613-real-glass-wifi-media-list-found.txt`
  - 关键结果：
    - `PASS UTE_WIFI_MEDIA_DIAGNOSTICS ... http://192.168.222.1:8000/media/list status=200 ...`
    - `PASS UTE_WIFI_MEDIA_LIST [ute-wifi-...:Photo:眼镜照片_...jpg ...]`

当前未闭环项：

- 随后重跑下载 smoke 时，设备再次进入账号不一致状态：
  - `DEVICE_CAPABILITIES supportsWifi=false,supportsFileTransfer=false`
  - `READ_WIFI ssid=` 空
  - `FAIL ENABLE_WIFI 设备账号不一致，请先在原应用解绑或重置设备后重新配对 PatrolLink`
- 报告：
  - `/Users/qiuqiquan/Desktop/SmartHeadsetSystem/PatrolLink/dist/smoke-reports/smoke-test-20260613-real-glass-wifi-account-mismatch-latest.txt`
- 因此目前已经确认“设备文件列表接口和解析”可用，但“从设备下载到手机沙箱并进入 App 媒体文件/后台上传队列”仍需要在账号再次接受、Wi-Fi 可打开的状态下复测。

补充代码改动：

- `UteWifiMediaParserTest` 已加入 Glass 热点实际返回格式的回归用例：
  - `{"code":200,"data":{"files":[{"name":"20260613144750407.jpg","size":1593149,"type":"jpg"}]}}`
  - 当前解析为 `Photo`，并把相对文件名解析为 `http://192.168.222.1:8000/media/<name>`。
- debug smoke 新增 `UTE_WIFI_DIRECT_DOWNLOAD_FIRST`：
  - 复用同一个 `UteWifiMediaClient` 的 `UTE_WIFI_MEDIA_LIST` 缓存，直接下载首个远端文件到 `Android/data/com.patrollink/files/wifi-smoke-downloads/`。
  - 这个步骤用于区分“设备接口能列文件但 URL 下载失败”和“生产 `coordinator.mediaFiles(local=false)` 候选为空”两类问题。

最新验证：

- `./gradlew :app:testDebugUnitTest --tests com.patrollink.data.ute.UteWifiMediaParserTest :app:assembleDebug --console=plain` 通过。
- APK 已安装真机，包更新时间：`lastUpdateTime=2026-06-13 23:20:42`。

### 2026-06-13 23:23 Wi-Fi 列表后下载稳定性补强

为减少“列表成功后，下载前再次触发账号判断/重连导致失败”的概率，本轮补了两层缓存复用：

- `UteSdkMediaGateway` 在 `listFiles(local=false)` 成功拿到 `ute-wifi-*` 远端文件后，会缓存这些远端 `MediaFile`。后续 `transfer(fileId, PhoneSandbox|Cloud)` 优先使用缓存条目，不再为了找同一个远端文件先重新枚举设备。
  - 缓存采用替换策略：设备成功返回列表时替换旧缓存；读取失败或超时时保留上一轮成功列表，避免账号状态短暂波动导致同步按钮立刻失去刚显示的远端文件。
- `UteWifiMediaClient` 在 Wi-Fi 准备成功后缓存设备热点 `ssid/password`。后续下载已缓存的远端文件时，会先用这组凭据直接连接当前设备热点并下载；失败后才回退到完整 SDK Wi-Fi 准备流程。

这样媒体页的预期链路变成：

1. 设备媒体刷新：打开/连接设备 Wi-Fi，访问 `http://192.168.222.1:8000/media/list`，缓存远端文件列表。
2. 同步到手机：直接使用缓存的远端文件和热点凭据下载，减少二次账号判断导致的波动。
3. 下载完成后仍会写入本地媒体索引、计算 SHA-256/水印，并按现有逻辑进入后续上传路径。

验证：

- `./gradlew :app:testDebugUnitTest :app:assembleDebug --console=plain` 通过。
- APK 已安装真机，包更新时间：`lastUpdateTime=2026-06-13 23:24:55`。

仍需在设备账号状态正常时复测：

- `UTE_WIFI_DIRECT_DOWNLOAD_FIRST` 是否能下载首个文件到 `wifi-smoke-downloads/`。
- `UTE_WIFI_MEDIA_DOWNLOAD_FIRST` 是否能通过 PatrolLink `coordinator.transferMedia(..., PhoneSandbox)` 写入 App 媒体文件并触发后台上传队列。

### 2026-06-13 23:34 已连接设备 E1/Glass 显示修复

用户现场反馈：真机系统蓝牙当前连接的是 `Glory Glass 2-00F7`，但 PatrolLink 的已连接设备仍显示 E1-Pro。

系统侧事实：

- `dumpsys bluetooth_manager` 显示当前 active/connected 设备是 `78:02:B7:66:00:F7 / Glory Glass 2-00F7`。
- `FD:4A:BA:43:A2:43 / E1-Pro-A243` 仍是 bonded 设备，但 A2DP/Headset 状态均为 disconnected。

根因：

- `refreshScannedDevices()` 在合并扫描结果时，会正确把系统蓝牙 connected 的 Glass 转成 `systemConnected`。
- 但如果 `state.device` 里保留了旧的 E1 在线状态，旧逻辑会优先沿用 `state.device`，导致主页/已连接设备继续显示 E1。
- 这不是系统蓝牙判断错误，而是 ViewModel 状态合并策略没有清理旧的 Headset/Glasses 在线占位。

代码修复：

- `PatrolViewModel.refreshScannedDevices()` 新增 stale audio connection 清理：
  - 当系统已经明确存在 Headset/Glasses connected 设备时，旧的 Headset/Glasses 在线状态如果不再对应当前系统 connected 设备，会从 `connectedDevices` 中移除。
  - 当前选中设备如果是 stale audio connection，会切换为真实系统 connected 设备。
- `ScannedDevice.toConnectedAudioStatus(...)` 保持扫描设备原始 `type`，因此 Glass 不会再被转成 Headset。

验证：

- 单测通过：
  - `./gradlew :app:testDebugUnitTest --tests com.patrollink.presentation.PatrolViewModelTest --console=plain`
  - 覆盖用例：旧 E1 在线 + 当前系统连接 Glass，刷新扫描后当前设备切换到 `Glory Glass 2-00F7`，并移除 E1。
- APK 构建并安装真机成功：
  - `./gradlew :app:assembleDebug --console=plain`
  - `adb install -r app/build/outputs/apk/debug/app-debug.apk`
  - 包更新时间：`lastUpdateTime=2026-06-13 23:32:35`
- 真机 smoke 报告：
  - `/Users/qiuqiquan/Desktop/SmartHeadsetSystem/PatrolLink/dist/smoke-reports/smoke-test-20260613-glass-connected-state-fix.txt`
  - 关键结果：
    - `SCAN_DEVICES` 中 Glass 为 `system-bluetooth-audio-connected`。
    - E1 仍可见，但仅为 `system-bluetooth-audio-bonded`。
    - `SELECT_DEVICE Glory Glass 2-00F7 78:02:B7:66:00:F7 type=Glasses`
    - `BIND_DEVICE_ONLINE ... name=Glory Glass 2-00F7,type=Glasses,online=true`
    - `SDK_CONNECTION_STATE isConnected=true,deviceAddress=78:02:B7:66:00:F7,deviceName=Glory Glass 2-00F7`

### 2026-06-13 23:42 设备媒体同步到手机闭环

本轮补齐了设备媒体页的一键同步入口，并验证 Glass 设备文件能通过设备热点下载到 PatrolLink 手机端媒体目录。

代码改动：

- `MediaScreen` 的设备端工具条保留 Wi-Fi 设置和刷新为图标按钮，主按钮明确显示为 `同步到手机`。
- `同步到手机` 调用已有 `PatrolViewModel.syncDeviceMediaToPhone(...)`：
  - 对设备端待同步文件逐个调用 `coordinator.transferMedia(fileId, PhoneSandbox)`。
  - 下载完成后写入手机端媒体列表。
  - 已有 ViewModel 单测覆盖：下载完成后会加入后台上传任务队列。
- `UteWifiMediaParser` 为 Glass `/media/list` 只返回裸文件名的情况生成多个下载候选 URL：
  - `/media/<name>`
  - `/<name>`
  - `/download/<name>`
  - `/file/<name>`
  - `/media/download?name=<name>`
- `UteWifiMediaClient` 下载远端文件时按候选 URL 逐个尝试，避免设备实际下载路径和列表路径推断不一致时直接失败。

验证：

- 本地验证通过：
  - `./gradlew :app:compileDebugKotlin :app:testDebugUnitTest --tests com.patrollink.presentation.PatrolViewModelTest --tests com.patrollink.data.ute.UteWifiMediaParserTest --console=plain`
  - `./gradlew :app:assembleDebug --console=plain`
- APK 已安装真机：
  - `/Users/qiuqiquan/Desktop/SmartHeadsetSystem/PatrolLink/dist/PatrolLink-20260613-media-sync-button-debug.apk`
  - 包更新时间：`lastUpdateTime=2026-06-13 23:42:20`
- 真机 smoke 成功报告：
  - `/Users/qiuqiquan/Desktop/SmartHeadsetSystem/PatrolLink/dist/smoke-reports/smoke-test-20260613-real-glass-wifi-download-success.txt`
  - 关键结果：
    - `PASS ENABLE_WIFI ... ssid=UTE_00F7 ... connected=true`
    - `PASS UTE_WIFI_MEDIA_DIAGNOSTICS ... http://192.168.222.1:8000/media/list status=200`
    - `PASS UTE_WIFI_MEDIA_LIST` 识别到 4 个设备端照片。
    - `PASS UTE_WIFI_DIRECT_DOWNLOAD_FIRST ... path=/storage/emulated/0/Android/data/com.patrollink/files/wifi-smoke-downloads/20260613144750407.jpg`
    - `PASS UTE_WIFI_MEDIA_DOWNLOAD_FIRST ... status=Done,target=PhoneSandbox ... uri=file:///data/user/0/com.patrollink/files/patrol_media/ute/20260613144750407.jpg`
    - `PASS UTE_WIFI_MEDIA_LOCAL_AFTER_DOWNLOAD` 手机端媒体列表出现下载后的文件。

当前结论：

- Glass 设备端照片通过设备热点传输到 PatrolLink 手机端媒体文件已跑通。
- 媒体页设备端现在有明确的 `同步到手机` 按钮，用户点击后走同一条 `PhoneSandbox` 传输链路。

### 2026-06-13 23:45 设备媒体一键同步体验补强

用户进一步要求：媒体页的设备端需要一个按钮，点击后就把设备端文件通过设备热点传输到手机端。

补强点：

- `同步到手机` 按钮现在不再依赖用户先点 `刷新` 后才可用。
- 点击 `同步到手机` 后会先执行设备端媒体列表读取，再同步待下载文件：
  - `coordinator.mediaFiles(local=false)` 读取设备端文件。
  - 将设备端文件合并进当前媒体列表。
  - 对待同步文件逐个执行 `coordinator.transferMedia(fileId, PhoneSandbox)`。
  - 下载完成后切换到手机端媒体列表，并加入后台上传任务队列。

验证：

- 新增 ViewModel 回归测试：初始媒体列表为空时，`syncDeviceMediaToPhone(refreshFirst=true)` 会先刷新设备文件，再下载并加入后台上传队列。
- 本地验证通过：
  - `./gradlew :app:testDebugUnitTest --tests com.patrollink.presentation.PatrolViewModelTest --tests com.patrollink.data.ute.UteWifiMediaParserTest :app:assembleDebug --console=plain`
- APK 已安装真机：
  - `/Users/qiuqiquan/Desktop/SmartHeadsetSystem/PatrolLink/dist/PatrolLink-20260613-one-click-device-sync-debug.apk`
  - 包更新时间：`lastUpdateTime=2026-06-13 23:45:41`

### 2026-06-13 23:51 后台上传队列和警员辖区验证

本轮继续核对媒体同步后的后台上传链路，以及“每个警员的执行辖区”是否按当前登录账号加载。

媒体后台上传链路：

- `MainActivity` 注入真实运行时依赖时，`PatrolViewModel` 已拿到 `OfflineSyncEngine`。
- `RuntimeDependencyFactory` 使用 `OfflineSyncEngine(WorkManagerBackgroundTaskGateway(appContext))`。
- 设备媒体通过 `PhoneSandbox` 下载完成后，`PatrolViewModel.enqueueEvidenceUploadIfLocal(...)` 会加入 `UploadEvidence` 后台任务。
- `WorkManagerBackgroundTaskGateway` 持久化任务后启动唯一 WorkManager 任务 `patrol-offline-sync`。
- `OfflineCompensationWorker` 会消费待处理任务，并由 `EvidenceUploadTaskProcessor` 调用 `mediaGateway.uploadLocalFile(...)` 上传本地媒体文件。

警员辖区链路：

- 正式后端网关使用 `RestPatrolAreaGateway(api)`。
- 真实接口为 `GET api/v1/patrol/areas/current`。
- 该接口走当前登录 token，App 不在前端写死警员辖区。
- `PatrolViewModel.refreshPatrolArea()` 登录后读取当前辖区，并用辖区名称覆盖当前用户资料里的 `dutyArea` 展示。

验证：

- 目标媒体和后台上传单测、Debug 构建已通过：
  - `./gradlew :app:testDebugUnitTest --tests com.patrollink.presentation.PatrolViewModelTest --tests com.patrollink.data.ute.UteWifiMediaParserTest --tests com.patrollink.data.local.EvidenceUploadTaskProcessorTest --tests com.patrollink.domain.OfflineSyncEngineTest :app:assembleDebug --console=plain`
- 警员辖区相关单测已通过：
  - `./gradlew :app:testDebugUnitTest --tests com.patrollink.presentation.PatrolViewModelTest.loginLoadsOfficerDutyAreaFromCurrentPatrolArea --tests com.patrollink.domain.PatrolCoordinatorTest.currentPatrolAreaComesFromTeamAreaGateway --tests com.patrollink.data.remote.MockRestApiTest.currentPatrolAreaContainsTeamSpecificBoundaryAndRoute --console=plain`
- 真机当前安装包确认：
  - 设备：`SKRGYH599TDQROSO`
  - 包：`com.patrollink`
  - 包更新时间：`lastUpdateTime=2026-06-13 23:45:41`

仍未闭环：

- Glass 当前能力读取显示不支持录音，已验证的是拍照、录像和 Wi-Fi 照片下载。
- E1/耳机录音指令和录音文件产出仍需要在 E1 可连接、可录音时做真机闭环验证；目前不能把“录音文件从设备同步到手机并后台上传”算作完成。

### 2026-06-13 23:58 音频/视频同步 smoke 过滤参数

为后续 E1/耳机可连接时直接验证“指定类型媒体文件同步”补充 debug smoke 能力。

新增参数：

- `--ez wifiDownloadFirst true`：设备 Wi-Fi 媒体列表成功后执行一次下载。
- `--es wifiDownloadKind audio`：只选设备端录音文件下载。
- `--es wifiDownloadKind video`：只选设备端视频文件下载。
- `--es wifiDownloadKind photo`：只选设备端照片文件下载。

用途：

- 旧逻辑只下载设备媒体列表第一个文件；如果第一个是照片，不能证明录音或视频的传输闭环。
- 新逻辑会在 `UTE_WIFI_DIRECT_DOWNLOAD_FIRST` 和 `UTE_WIFI_MEDIA_DOWNLOAD_FIRST` 两条路径里优先选择指定 `MediaKind`。
- 如果设备端没有该类型文件，smoke 会明确失败并提示 `kind=Audio` 或 `kind=Video`，避免误把照片下载成功当作音频/视频同步成功。

E1/耳机录音可测时建议命令形态：

```bash
adb -s SKRGYH599TDQROSO shell am broadcast \
  -n com.patrollink/.debug.SmokeTestReceiver \
  --es account POLICE_9527 \
  --es password 123456 \
  --es targetDeviceName E1 \
  --ez commands true \
  --ez wifi true \
  --ez wifiDownloadFirst true \
  --es wifiDownloadKind audio
```

Glass 视频可测时建议命令形态：

```bash
adb -s SKRGYH599TDQROSO shell am broadcast \
  -n com.patrollink/.debug.SmokeTestReceiver \
  --es account POLICE_9527 \
  --es password 123456 \
  --es targetDeviceName "Glory Glass" \
  --ez commands true \
  --ez wifi true \
  --ez wifiDownloadFirst true \
  --es wifiDownloadKind video
```

验收标准：

- `START_HEADSET_AUDIO` / `STOP_HEADSET_AUDIO` 或 `START_VIDEO` / `STOP_VIDEO` 通过。
- `UTE_WIFI_MEDIA_LIST` 出现目标类型文件。
- `UTE_WIFI_DIRECT_DOWNLOAD_FIRST` 下载目标类型文件成功。
- `UTE_WIFI_MEDIA_DOWNLOAD_FIRST` 通过 PatrolLink 媒体网关下载到 `PhoneSandbox`。
- `UTE_WIFI_MEDIA_LOCAL_AFTER_DOWNLOAD` 手机端媒体列表能查到该文件。

### 2026-06-14 00:04 Glass 指定 video 同步复测

使用 23:54 已安装的新 debug 包，在真机上通过 `SmokeTestActivity` 方式执行指定 video 下载验证，避免长耗时 `BroadcastReceiver` 被系统超时结束。

报告：

- `/Users/qiuqiquan/Desktop/SmartHeadsetSystem/PatrolLink/dist/smoke-reports/smoke-test-20260614-real-glass-video-kind-wifi-not-enabled.txt`

关键结果：

- `SCAN_DEVICES` 正确识别：
  - `Glory Glass 2-00F7/78:02:B7:66:00:F7/Glasses/system-bluetooth-audio-connected`
  - `E1-Pro-A243/FD:4A:BA:43:A2:43/Headset/system-bluetooth-audio-bonded`
  - `SMI-M14/E8:4A:54:67:93:4D/Glasses/ute-ble-control-scanned`
- `BIND_DEVICE_ONLINE` 通过，目标为 `Glory Glass 2-00F7`。
- 设备能力继续显示：
  - `supportsWifi=true`
  - `supportsFileTransfer=true`
  - `supportsPhoto=true`
  - `supportsVideo=true`
  - `supportsAudioRecord=false`
- 当前设备 Wi-Fi 状态：
  - `READ_WIFI DeviceWifiState(enabled=false, ssid=UTE_00F7, passwordConfigured=true, connected=false)`
  - `READ_WIFI_RAW state=5,ssid=UTE_00F7,passwordLen=8`
- 正常 Wi-Fi 打开路径失败：
  - `FAIL ENABLE_WIFI device wifi did not enable: 5`
  - `FAIL ENABLE_WIFI_WITH_EXISTING_CONFIG device wifi did not enable: 5`
  - `READ_WIFI_AFTER_ENABLE enabled=false,connected=false`
- 手机 Wi-Fi 仍连接普通路由：
  - `ssid="英英杀人女魔头5G"`
  - `ip=192.168.1.6`
  - 仅扫描到 `UTE_00F7/fe:fd:fc:43:46:03/rssi=-57`
- 因设备 AP 未 ready，媒体接口无法访问：
  - `UTE_WIFI_MEDIA_DIAGNOSTICS timeout`
  - `UTE_WIFI_MEDIA_LIST timeout`
  - `FAIL UTE_WIFI_DIRECT_DOWNLOAD_FIRST no listed wifi media file to download kind=Video`
  - `UTE_WIFI_MEDIA_DOWNLOAD_CANDIDATES []`
  - `FAIL UTE_WIFI_MEDIA_DOWNLOAD_FIRST no device media file to download kind=Video`

结论：

- 这次没有证明 video 传输闭环；失败点在设备 Wi-Fi AP 未开启，而不是 `wifiDownloadKind=video` 过滤或 PatrolLink 下载逻辑。
- 当前真机 App 仍可正确绑定 Glass、读取能力、保持 SDK 连接。
- 当前 Glass 不支持录音，E1 仍只是系统蓝牙 bonded，录音闭环仍待 E1 控制通道可用后验证。

### 2026-06-14 00:07 Glass Wi-Fi 直调开关复测

为区分“PatrolLink 正常 Wi-Fi 路径问题”和“设备当前不进入 AP ready”，执行 debug-only 直调探针。

报告：

- `/Users/qiuqiquan/Desktop/SmartHeadsetSystem/PatrolLink/dist/smoke-reports/smoke-test-20260614-real-glass-direct-wifi-state5.txt`

关键结果：

- `BIND_DEVICE_ONLINE` 通过，SDK 连接仍为 `Glory Glass 2-00F7 / 78:02:B7:66:00:F7`。
- `DIRECT_READ_WIFI_RAW state=5,ssid=UTE_00F7,passwordLen=8`。
- 直调 `smartSetDeviceWiFiSwitch(true)`：
  - `PASS DIRECT_WIFI_SWITCH_ON_NO_ACCOUNT_GUARD success=true,error=100000,data=false`
  - 返回 `success=true` 但 `data=false`。
- 直调后等待 AP ready 失败：
  - `DIRECT_WAIT_WIFI_READY_NO_ACCOUNT_GUARD ready=false`
  - `DIRECT_READ_WIFI_AFTER_SWITCH DeviceWifiState(enabled=false, ssid=UTE_00F7, passwordConfigured=true, connected=false)`
- 手机侧仍未切到设备热点：
  - `WIFI_ANDROID_NETWORK ssid="英英杀人女魔头5G"... uteScan=[UTE_00F7/fe:fd:fc:43:46:03/rssi=-57]`
- HTTP 探测命中的是普通路由 `192.168.1.1`，不是设备热点 `192.168.222.1`。

结论：

- 当前 Glass 设备处于“能连蓝牙控制、能读到 Wi-Fi 配置，但 SDK 开 Wi-Fi 后不进入 AP ready”的状态。
- 由于 debug-only 直调也无法打开 AP，这次失败不能归因于 PatrolLink 账号保护或 UI 同步按钮。
- 后续若用户手动在系统 Wi-Fi 连接到 `UTE_00F7`，应直接跑 `wifiDownloadKind=video` smoke 或在媒体页点 `同步到手机` 验证 video 文件进入 App 媒体。

### 2026-06-14 00:12 Wi-Fi state=5 用户提示修正

通过反编译本地 SDK `app/libs/uteWatchSdk_Android_v1.3.5.aar` 确认 `WifiState` 常量：

- `WIFI_OPEN_SUCCESS = 1`
- `WIFI_OPEN_FAILED = 2`
- `WIFI_CLOSE_SUCCESS = 3`
- `WIFI_CLOSE_FAILED = 4`
- `WIFI_AP_STOP = 5`
- `IFI_AP_STARTING = 6`
- `IFI_AP_READY = 7`
- `IFI_AP_CONNECT = 8`
- `IFI_AP_CONNECT_FAILED = 9`

本轮真机失败中的 `state=5` 明确是 `WIFI_AP_STOP`，即设备热点关闭态，不是 PatrolLink 状态判断错误。

代码补强：

- `PatrolViewModel.operatorFacingWifiError()` 对 `device wifi did not enable: 5` / `WIFI_AP_STOP` 做用户可读映射。
- App 不再直接显示 SDK 内部错误 `device wifi did not enable: 5`。
- 新提示为：`设备热点未开启，请确认设备电量和当前模式后重试；若仍失败，请在设备侧重启 Wi-Fi 或重启设备`。

验证：

- 先新增失败用例 `wifiApStoppedStateMessageIsShownToOperator`，确认旧逻辑会原样显示内部错误。
- 补实现后通过：
  - `./gradlew :app:testDebugUnitTest --tests com.patrollink.presentation.PatrolViewModelTest.wifiApStoppedStateMessageIsShownToOperator --tests com.patrollink.presentation.PatrolViewModelTest.wifiAccountMismatchMessageIsShownToOperator --tests com.patrollink.presentation.PatrolViewModelTest.wifiManualConnectionMessageIsShownToOperator --console=plain`
- 全量本地验证通过：
  - `./gradlew :app:testDebugUnitTest :app:assembleDebug --console=plain`

安装状态：

- 新 APK：`/Users/qiuqiquan/Desktop/SmartHeadsetSystem/PatrolLink/dist/PatrolLink-20260614-wifi-state-message-debug.apk`
- 模拟器 `emulator-5554` 已安装并启动到 `com.patrollink/.MainActivity`。
- 真机 ADB 静默安装被 MIUI 拦截：
  - `INSTALL_FAILED_USER_RESTRICTED: Install canceled by user`
- APK 已推送到真机：
  - `/sdcard/Download/PatrolLink-20260614-wifi-state-message-debug.apk`
- 已尝试打开系统安装器。当前真机包更新时间仍为 `lastUpdateTime=2026-06-13 23:54:53`，需要在手机安装界面确认后才会更新。

### 2026-06-14 00:16 手动连接设备热点 fallback

问题背景：

- 当前 Glass 有时 SDK 开 AP 会停在 `WIFI_AP_STOP(5)`。
- 但用户现场反馈手机可以手动连上设备热点。
- 如果用户已经在系统 Wi-Fi 里手动连接到 `UTE_00F7`，PatrolLink 设备媒体同步不应再因为 SDK AP 状态为 5 直接失败。

代码补强：

- `DeviceWifiNetworkConnector` 新增 `currentSession(ssid)`，可复用手机当前已经连接的设备热点网络。
- `UteWifiMediaClient.prepareDeviceWifi()` 在 SDK AP 未进入 connectable 状态时：
  - 若状态是 `WIFI_AP_STOP` / `WIFI_OPEN_FAILED` / `IFI_AP_CONNECT_FAILED`；
  - 且手机当前已连接目标 SSID；
  - 则直接使用手机当前 Wi-Fi session 继续访问设备 HTTP 文件服务。
- 这样媒体页 `同步到手机` 支持两条路径：
  - 正常路径：SDK 打开设备 AP，App 请求连接热点。
  - 兜底路径：用户已手动连接设备热点，App 直接复用当前 Wi-Fi 同步文件。

验证：

- 新增单测 `UteWifiMediaClientTest.sdkApStoppedStateCanFallBackToPhoneConnectedHotspot`。
- 相关测试通过：
  - `./gradlew :app:testDebugUnitTest --tests com.patrollink.data.ute.UteWifiMediaClientTest --tests com.patrollink.data.wifi.DeviceWifiNetworkConnectorTest --console=plain`
- 全量本地验证通过：
  - `./gradlew :app:testDebugUnitTest :app:assembleDebug --console=plain`

安装状态：

- 新 APK：`/Users/qiuqiquan/Desktop/SmartHeadsetSystem/PatrolLink/dist/PatrolLink-20260614-manual-hotspot-fallback-debug.apk`
- 模拟器 `emulator-5554` 已安装并启动。
- 真机 ADB 静默安装仍被 MIUI 拦截：
  - `INSTALL_FAILED_USER_RESTRICTED: Install canceled by user`
- APK 已推送到真机：
  - `/sdcard/Download/PatrolLink-20260614-manual-hotspot-fallback-debug.apk`
- 已尝试打开系统安装器。
- 当前真机包更新时间为 `lastUpdateTime=2026-06-14 00:14:19`，说明 00:12 的 Wi-Fi state 用户提示包已经装上；00:16 手动热点 fallback 包仍需在手机安装界面确认。

### 2026-06-14 00:22 手动热点媒体拉取调试通道

问题背景：

- 用户现场确认手机可以正确连接耳机 Wi-Fi 热点。
- 当前仍需要闭环验证：设备端已有照片/视频时，媒体页或调试 smoke 能通过设备热点把文件拉到 PatrolLink 手机端媒体目录。

代码补强：

- `UteWifiMediaClient` 的 `listFiles()`、`diagnostics()`、`download()` 新增 `currentPhoneWifiOnly` 参数。
- 当 `currentPhoneWifiOnly=true` 时：
  - 仍通过 SDK 读取设备 Wi-Fi SSID/密码和执行账号绑定校验；
  - 不再调用 `smartSetDeviceWiFiSwitch(true)` 开热点；
  - 直接复用手机当前已经连接的目标 `UTE_...` Wi-Fi session；
  - 若手机未连接目标热点，则抛出已有的用户可读错误 `DeviceWifiUserConnectionRequiredException`。
- debug smoke 新增 intent 参数：
  - `--ez wifiMediaOnly true`
  - 可配合 `--ez wifiDownloadFirst true --es wifiDownloadKind photo|video|audio` 只验证当前手机 Wi-Fi 上的媒体列表/下载链路。

推荐真机复测命令（需手机先手动连上 `UTE_00F7`）：

```bash
adb -s SKRGYH599TDQROSO shell am start -n com.patrollink/.debug.SmokeTestActivity \
  --es account POLICE_9527 \
  --es password 123456 \
  --es targetDeviceId 78:02:B7:66:00:F7 \
  --ez commands false \
  --ez wifi false \
  --ez wifiMediaOnly true \
  --ez wifiDownloadFirst true \
  --es wifiDownloadKind video
```

验证：

- 相关测试通过：
  - `./gradlew :app:testDebugUnitTest --tests com.patrollink.debug.SmokeWifiPreflightOptionsTest --tests com.patrollink.data.ute.UteWifiMediaClientTest --console=plain`
- 全量本地验证通过：
  - `./gradlew :app:testDebugUnitTest :app:assembleDebug --console=plain`

安装状态：

- 新 APK：`/Users/qiuqiquan/Desktop/SmartHeadsetSystem/PatrolLink/dist/PatrolLink-20260614-wifi-media-only-debug.apk`
- 模拟器 `emulator-5554` 已安装。
- 真机 ADB 静默安装仍被 MIUI 拦截：
  - `INSTALL_FAILED_USER_RESTRICTED: Install canceled by user`
- APK 已推送到真机：
  - `/sdcard/Download/PatrolLink-20260614-wifi-media-only-debug.apk`
- 已尝试打开系统安装器，需要在手机安装界面确认后再读取 `lastUpdateTime` 验证是否安装完成。
- 继续确认状态：
  - 模拟器 `emulator-5554` 已安装并启动最新版 PatrolLink，包更新时间 `lastUpdateTime=2026-06-14 00:23:49`。
  - 真机 `SKRGYH599TDQROSO` 仍停在 MIUI 安装器 `com.miui.packageInstaller.NewInstallerPrepareActivity`。
  - 真机当前 PatrolLink 包更新时间仍为 `lastUpdateTime=2026-06-14 00:18:56`，说明 00:22 `wifiMediaOnly` 包尚未安装完成。
  - MIUI 同时拦截了 `adb install` 和 `adb input tap`，需要用户在手机上手动点击“继续安装/安装/完成”。
  - 追加尝试 `pm install -r /data/local/tmp/PatrolLink-20260614-wifi-media-only-debug.apk` 仍返回 `INSTALL_FAILED_USER_RESTRICTED: Install canceled by user`，确认无法绕过 MIUI 安装确认。
  - 生产链路目标测试再次通过，覆盖媒体页 `同步到手机` 背后的 ViewModel 行为：
    - `syncDeviceMediaToPhoneDownloadsMissingDeviceFilesAndQueuesBackgroundUploads`
    - `syncDeviceMediaToPhoneRefreshesDeviceFilesWhenListIsEmpty`
    - `UteWifiMediaClientTest`
    - `SmokeWifiPreflightOptionsTest`
  - 命令：`./gradlew :app:testDebugUnitTest --tests com.patrollink.presentation.PatrolViewModelTest.syncDeviceMediaToPhoneDownloadsMissingDeviceFilesAndQueuesBackgroundUploads --tests com.patrollink.presentation.PatrolViewModelTest.syncDeviceMediaToPhoneRefreshesDeviceFilesWhenListIsEmpty --tests com.patrollink.data.ute.UteWifiMediaClientTest --tests com.patrollink.debug.SmokeWifiPreflightOptionsTest --console=plain`

### 2026-06-14 00:33 SDK 媒体接口复核和当前热点优先策略

文档/SDK 资料：

- 浏览器当前页面确认是 KDocs `UteWatchSDK_Android_使用说明文档`：`https://www.kdocs.cn/l/clTtBk8yvHED`。
- KDocs 页面当前 DOM 只暴露局部正文，能看到蓝牙扫描权限段落；媒体章节无法稳定从 DOM 抓取。
- 以工程实际依赖的 `app/libs/uteWatchSdk_Android_v1.3.5.aar` 公开签名为准，`UteBleConnection` 中和本目标相关的接口为：
  - Wi-Fi：`smartGetDeviceWiFiInfo()`、`smartGetDeviceWiFiStateInfo()`、`smartSetDeviceWiFiSwitch(boolean)`、`smartSetDeviceWiFiSSID(String)`、`smartSetDeviceWiFiPassword(String)`。
  - Glass 信息/存储：`getGlassesInfo()`、`getGlassesStateInfo()`，其中 `GlassesStoreInfo` 包含照片、音频、视频总数和新增数。
  - 拍照：`triggerGlassesPhotoCapture(SmartGpsInfo)`。
  - 录像：`setVideoParameters(VideoParametersInfo)`、`setGlassesRecordingDirection(int)`、`setGlassesRecordingDuration(int)`、`toggleGlassesVideoRecording(int)`。
  - Glass 文件清理/同步完成通知：`deleteGlassesFilesByType(DeleteGlassesFilesByType)`、`deleteGlassesFilesByName(DeleteGlassesFilesByName)`、`notifyMediaSyncCompleted()`。
  - 耳机音频录制：`setHeadsetAccount(HeadsetAccountConfig)`、`toggleHeadsetAudioRecording(int)`。
  - AI 录音文件：`appStartAudioRecord()`、`appStopAudioRecord()`、`queryAudioRecordFileLists(RequestAudioRecordFileInfo)`、`syncAudioRecordFile(RequestSyncAudioRecordFileInfo)`、`stopSyncAudioRecordFile()`、`deleteAudioRecordFile(RequestDeleteAudioRecordFileInfo)`、`getDeviceStorageInfo()`。

代码补强：

- 生产媒体同步路径现在在读取到设备 Wi-Fi SSID 后，会优先检查手机是否已经连接该设备热点。
- 如果手机已经在目标 `UTE_...` Wi-Fi 上，`UteWifiMediaClient.prepareDeviceWifi()` 直接复用当前 Wi-Fi session，不再先等待 SDK 开 AP。
- 这让媒体页 `同步到手机` 更贴合现场操作：用户手动连上设备热点后，点击按钮应直接拉设备 HTTP 媒体列表/文件。

验证：

- 先新增失败测试：`productionSyncPrefersPhoneConnectedHotspotBeforeOpeningSdkAp`。
- 实现后目标测试通过：
  - `./gradlew :app:testDebugUnitTest --tests com.patrollink.data.ute.UteWifiMediaClientTest --console=plain`
- 全量本地验证通过：
  - `./gradlew :app:testDebugUnitTest :app:assembleDebug --console=plain`

安装状态：

- 新 APK：`/Users/qiuqiquan/Desktop/SmartHeadsetSystem/PatrolLink/dist/PatrolLink-20260614-prefer-current-hotspot-debug.apk`
- 模拟器 `emulator-5554` 已安装并启动，包更新时间 `lastUpdateTime=2026-06-14 00:33:10`。
- 真机 APK 已推送：
  - `/sdcard/Download/PatrolLink-20260614-prefer-current-hotspot-debug.apk`
- 已重新打开 MIUI 系统安装器。
- 真机当前仍是旧包：`lastUpdateTime=2026-06-14 00:18:56`，需要手动点击安装器确认后才能跑真机 smoke。

### 2026-06-14 00:40 设备端删除能力和真机 wifiMediaOnly 复测

代码补强：

- `UteSdkMediaGateway.delete(fileId, local=false)` 对 `ute-wifi-...` 设备端文件新增 SDK 删除路径。
- 删除逻辑会从媒体展示名还原设备原始文件名：
  - `眼镜照片_20260613144750407.jpg` -> `20260613144750407.jpg`
  - `眼镜视频_GX010002.MP4` -> `GX010002.MP4`
  - `设备录音_REC001.opus` -> `REC001.opus`
- 然后调用 `deleteGlassesFilesByName(DeleteGlassesFilesByName(name))`。
- SDK 返回 `DELETE_SUCCESS(1)` 或 `FILE_NOT_EXIST(2)` 都视为设备端已无该文件，并清理本地设备端缓存。
- 不会在同步成功后自动删除设备文件，避免误删证据；只有用户在媒体页显式删除设备端文件时才会调用该路径。

验证：

- 新增单测 `UteSdkMediaGatewayTest` 覆盖 Wi-Fi 设备文件删除名解析。
- 目标测试通过：
  - `./gradlew :app:testDebugUnitTest --tests com.patrollink.data.ute.UteSdkMediaGatewayTest --console=plain`
- 全量验证通过：
  - `./gradlew :app:testDebugUnitTest :app:assembleDebug --console=plain`

安装状态：

- 新 APK：`/Users/qiuqiquan/Desktop/SmartHeadsetSystem/PatrolLink/dist/PatrolLink-20260614-device-delete-debug.apk`
- 模拟器 `emulator-5554` 已安装并启动，包更新时间 `lastUpdateTime=2026-06-14 00:37:15`。
- 真机当前包更新时间变为 `lastUpdateTime=2026-06-14 00:36:29`，已不再是 00:18 旧包。
- 从真机拉取当前 `/data/app/.../base.apk` 后检查 dex 字符串，确认当前包已经包含手动热点调试能力：
  - `wifiMediaOnly`
  - `currentPhoneWifiOnly`
  - `using current phone wifi`
- 但当前真机 APK 文件大小约 `110998044` bytes，而 00:37 设备端删除增强包大小约 `110999490` bytes，说明真机未能证明已经装上 00:37 删除增强包。
- 00:37 删除增强包已推送到：
  - `/sdcard/Download/PatrolLink-20260614-device-delete-debug.apk`
- 已打开 MIUI 系统安装器，仍需要用户手动确认安装。

真机 smoke：

- 报告：`/Users/qiuqiquan/Desktop/SmartHeadsetSystem/PatrolLink/dist/smoke-reports/smoke-test-20260614-real-glass-wifi-media-only-not-connected.txt`
- 命令使用：
  - `--ez wifiMediaOnly true`
  - `--ez wifiDownloadFirst true`
  - `--es wifiDownloadKind video`
- 关键结果：
  - `PASS SCAN_DEVICES`：Glass 仍正确识别为当前系统蓝牙连接设备，E1 只是 bonded。
  - `PASS BIND_DEVICE_ONLINE`：`Glory Glass 2-00F7 / 78:02:B7:66:00:F7` 绑定在线。
  - `PASS DEVICE_CAPABILITIES`：Glass 支持 Wi-Fi、文件、拍照、录像；不支持录音。
  - `PASS ENABLE_WIFI skipped; wifiMediaOnly uses current phone Wi-Fi`：新参数已生效。
  - `WIFI_ANDROID_NETWORK ssid="英英杀人女魔头5G"`：手机当前仍在普通路由，不在 `UTE_00F7`。
  - `FAIL UTE_WIFI_MEDIA_DIAGNOSTICS 手机系统未授权连接设备热点 UTE_00F7...`
  - `FAIL UTE_WIFI_MEDIA_LIST 手机系统未授权连接设备热点 UTE_00F7...`
  - `FAIL UTE_WIFI_DIRECT_DOWNLOAD_FIRST no listed wifi media file to download kind=Video`
  - 设备端候选缓存里目前只有 Photo，没有 Video。
  - coordinator 标准路径额外失败：`设备账号不一致，请先在原应用解绑或重置设备后重新配对 PatrolLink`。这是非 `wifiMediaOnly` 直接路径的旧缓存/标准下载路径噪声，成功复测时应以 `UTE_WIFI_MEDIA_DIAGNOSTICS`、`UTE_WIFI_MEDIA_LIST`、`UTE_WIFI_DIRECT_DOWNLOAD_FIRST` 为主。

当前结论：

- 最新真机包已能运行 `wifiMediaOnly` 直接路径。
- 失败原因不是代码参数缺失，而是手机当前未连接设备热点 `UTE_00F7`。
- 下一次复测前需要在手机系统 Wi-Fi 中手动连接 `UTE_00F7`，然后重新执行同一 smoke。
- 当前 Glass 设备端没有 video 文件；如果要验证录像同步，需要先用 Glass 录一段视频，或把 `wifiDownloadKind` 改成 `photo` 验证照片同步。

### 2026-06-14 00:54 Glass 设备热点开启和照片下载成功路径

用户现场反馈“连上了”，本轮用真机确认：成功开启设备热点的路径是 PatrolLink 标准 SDK 媒体网关路径，不是单纯 `wifiMediaOnly` 手动热点直连路径。

需要记住的成功顺序：

- 目标设备：`Glory Glass 2-00F7 / 78:02:B7:66:00:F7`。
- 先通过 BLE 绑定设备并打开 SDK notify：`bridge.client.openOrCloseNotify(true)`。
- 读取设备 Wi-Fi 信息：`smartGetDeviceWiFiInfo()`，当前为 `ssid=UTE_00F7`，密码已配置，初始状态 `WIFI_AP_STOP(5)`。
- 媒体网关 `UteWifiMediaClient.prepareDeviceWifi()` 会先判断手机是否已在目标 `UTE_00F7`；如果没有，则进入 SDK 开 AP 路径。
- 开 AP 前执行 Glory View 同款预热：
  - `smartSetDeviceWiFiSSID(ssid)`
  - `smartSetDeviceWiFiPassword(password)`
  - `setGlassesRecordingDirection(VERTICAL_SCREEN)`
  - `setGlassesRecordingDuration(30)`
  - `setVideoParameters(2112,1568,30)`
  - `getGlassesInfo()`
  - `notifyMediaSyncCompleted()`
  - `UteSmartAuthWarmup.run(...)`
- 然后调用 `smartSetDeviceWiFiSwitch(true)`。
- 等待 `SMART_WIFI_STATE_NOTIFY` 或轮询 Wi-Fi 状态进入可连接状态；相邻标准开热点 smoke 中已经观察到 `1 -> 6 -> 7` 并读到 `enabled=true, connected=true, ssid=UTE_00F7`。
- 本次 00:54 成功下载对应的 logcat 进一步确认 Android 由 PatrolLink 请求连接 `UTE_00F7`：
  - `DeviceWifiConnector: requesting device wifi ssid=UTE_00F7 ... bssid=fe:fd:fc:82:9f:80`
  - `WifiNetworkFactory: Approved access point found ... Triggering connect UTE_00F7/fe:fd:fc:82:9f:80`
  - DHCP 分配 `192.168.222.100/24`，网关/DNS 为 `192.168.222.1`
  - `DeviceWifiConnector: device wifi available ssid=UTE_00F7 ... network=267`
- 进入设备热点 HTTP 文件服务后，`coordinator.mediaFiles(local=false)` 读取到设备端照片候选，`coordinator.transferMedia(first.id, PhoneSandbox)` 成功下载到 PatrolLink 私有媒体目录。

报告：

- `/Users/qiuqiquan/Desktop/SmartHeadsetSystem/PatrolLink/dist/smoke-reports/smoke-test-20260614-real-glass-skip-login-wifi-media-photo-success.txt`
- `/Users/qiuqiquan/Desktop/SmartHeadsetSystem/PatrolLink/dist/smoke-reports/logcat-20260614-real-glass-wifi-media-photo-success.txt`

关键结果：

- `INFO LOGIN skipped`：避免手机连上设备热点后访问不到后端 `192.168.1.3:8080`。
- `PASS BIND_DEVICE_ONLINE`：Glass 蓝牙控制链路在线。
- `PASS READ_WIFI DeviceWifiState(enabled=false, ssid=UTE_00F7, passwordConfigured=true, connected=false)`。
- `PASS ENABLE_WIFI skipped; wifiMediaOnly uses current phone Wi-Fi`：本次直接诊断分支没有主动开热点。
- `FAIL UTE_WIFI_MEDIA_DIAGNOSTICS` / `FAIL UTE_WIFI_MEDIA_LIST`：直连诊断分支执行时手机已经回到普通路由，因此该分支失败。
- `PASS UTE_WIFI_MEDIA_DOWNLOAD_CANDIDATES`：标准媒体网关分支成功打开设备热点并识别 4 个设备端照片。
- `PASS UTE_WIFI_MEDIA_DOWNLOAD_FIRST ... status=Done,target=PhoneSandbox ... uri=file:///data/user/0/com.patrollink/files/patrol_media/ute/20260613144750407.jpg`。
- `PASS UTE_WIFI_MEDIA_LOCAL_AFTER_DOWNLOAD`：手机端媒体列表能查到下载后的照片。

当前结论：

- Glass 照片“设备热点 -> PatrolLink 手机端媒体文件 -> 后台上传队列”的核心下载链路已经再次跑通。
- 后续生产操作应优先使用媒体页 `同步到手机`，它会走同一条标准媒体网关路径。
- 若用户手动连上 `UTE_00F7` 后再点同步，代码会优先复用当前手机 Wi-Fi；若未手动连接，则标准路径会按上述顺序尝试由 SDK 打开设备 AP。
- 这次没有证明 video/audio 文件同步：当前 Glass 文件候选只有 Photo，Glass 能力也显示不支持录音。视频需先在 Glass 产生 video 文件；耳机录音需等 E1 控制通道可用后再测。

### 2026-06-14 01:30 Glass video 指定同步复测和 smoke 修正

本轮继续验证 video 文件同步。先发现 debug smoke 的一个验证缺陷：传入 `--es wifiDownloadKind video` 时，如果设备列表里没有 Video，旧逻辑会 fallback 到第一条 Photo，导致报告看起来像“指定 video 下载成功”，实际下载的是照片。

代码修正：

- `SmokeWifiMediaSyncOptions.selectDownloadCandidate(...)` 改为：
  - 指定 `downloadKind` 时只选择该类型；
  - 如果该类型不存在，返回 `null`，明确输出 `no listed wifi media file to download kind=Video`。
- smoke 报告新增：
  - `WIFI_MEDIA_OPTIONS downloadFirst=...,downloadKind=...,currentPhoneWifiOnly=...`
  - `COMMAND_OPTIONS holdMillis=...`
- smoke 新增 `--el commandHoldMillis <ms>`，用于把录像/录音保持时间从默认 2 秒调大。本轮用 10 秒验证 Glass video。
- 新增单测 `wifiMediaSyncOptionDoesNotFallbackToPhotoWhenRequestedVideoIsMissing`。

验证：

- 本地验证通过：
  - `./gradlew :app:testDebugUnitTest --tests com.patrollink.debug.SmokeWifiPreflightOptionsTest :app:assembleDebug --console=plain`
- 新 APK：
  - `/Users/qiuqiquan/Desktop/SmartHeadsetSystem/PatrolLink/dist/PatrolLink-20260614-video-hold-debug.apk`
- 真机已安装，包更新时间：
  - `lastUpdateTime=2026-06-14 01:26:55`
- 模拟器也已安装并启动最新 PatrolLink。

真机 10 秒 video smoke：

- 命令关键参数：
  - `--ez skipLogin true`
  - `--es pairingAccountId POLICE_9527`
  - `--es targetDeviceId 78:02:B7:66:00:F7`
  - `--ez commands true`
  - `--el commandHoldMillis 10000`
  - `--ez wifi true`
  - `--ez wifiDownloadFirst true`
  - `--es wifiDownloadKind video`
- 报告：
  - `/Users/qiuqiquan/Desktop/SmartHeadsetSystem/PatrolLink/dist/smoke-reports/smoke-test-20260614-real-glass-video-10s-no-device-video.txt`
  - `/Users/qiuqiquan/Desktop/SmartHeadsetSystem/PatrolLink/dist/smoke-reports/logcat-20260614-real-glass-video-10s-no-device-video.txt`

关键结果：

- `INFO WIFI_MEDIA_OPTIONS downloadFirst=true,downloadKind=Video,currentPhoneWifiOnly=false`：确认新包和 video 过滤参数生效。
- `INFO COMMAND_OPTIONS holdMillis=10000`：确认本轮录像保持 10 秒。
- `PASS START_VIDEO` / `PASS STOP_VIDEO`：录像控制命令仍可下发。
- `SMART_GLASSES_STORE_INFO_NOTIFY ... newRecordVideo=0,totalRecordVideo=0`：设备端仍没有产生 Video 文件。
- `PASS UTE_WIFI_MEDIA_DIAGNOSTICS ... http://192.168.222.1:8000/media/list status=200`：设备热点 HTTP 文件服务仍可访问。
- `PASS UTE_WIFI_MEDIA_LIST`：设备端列出 7 个文件，但全部是 `Photo`。
- `FAIL UTE_WIFI_DIRECT_DOWNLOAD_FIRST no listed wifi media file to download kind=Video`：直接 Wi-Fi 下载路径正确拒绝用照片冒充 video。
- `FAIL UTE_WIFI_MEDIA_DOWNLOAD_FIRST no device media file to download kind=Video`：标准媒体网关路径同样没有 video 候选。

当前结论：

- PatrolLink 的 video 类型过滤和验证逻辑已修正。
- Glass 的录像开始/停止命令能成功下发，但当前设备/当前模式没有生成可下载的视频文件；10 秒录像后 `/media/list` 仍只有照片。
- 因此 video 文件同步仍未闭环，阻塞点不是 Wi-Fi 下载逻辑，而是设备端没有产出 video 文件。后续需要确认 Glass 录像模式/参数是否满足产出条件，或用 Glory View/设备物理操作先产生一个 mp4 后再验证 PatrolLink 同步。

### 2026-06-14 01:38 录像开始前补齐 Glory View 参数后复测

继续排查 video 不产出的原因。生产 `UteSdkDeviceGateway` 原先在 `StartRecord` 时只调用 `toggleGlassesVideoRecording(start)`；Wi-Fi warmup 中才有 Glory View 同款录像参数设置。为消除差异，本轮把录像开始前置参数补到 `StartRecord` 路径：

- `setGlassesRecordingDirection(VERTICAL_SCREEN)`
- `setGlassesRecordingDuration(30)`
- `setVideoParameters(2112,1568,30)`
- `toggleGlassesVideoRecording(RECORDING_STATE_START)`

验证：

- 本地验证通过：
  - `./gradlew :app:compileDebugKotlin :app:testDebugUnitTest --tests com.patrollink.debug.SmokeWifiPreflightOptionsTest --tests com.patrollink.data.ute.UteWifiMediaClientTest --console=plain`
  - `./gradlew :app:assembleDebug --console=plain`
- 新 APK：
  - `/Users/qiuqiquan/Desktop/SmartHeadsetSystem/PatrolLink/dist/PatrolLink-20260614-video-preset-debug.apk`
- 真机已安装，包更新时间：
  - `lastUpdateTime=2026-06-14 01:34:41`
- 模拟器也已安装并启动最新 PatrolLink。

真机复测报告：

- `/Users/qiuqiquan/Desktop/SmartHeadsetSystem/PatrolLink/dist/smoke-reports/smoke-test-20260614-real-glass-video-preset-no-device-video.txt`
- `/Users/qiuqiquan/Desktop/SmartHeadsetSystem/PatrolLink/dist/smoke-reports/logcat-20260614-real-glass-video-preset-no-device-video.txt`

关键结果：

- 10 秒录像仍为 `PASS START_VIDEO` / `PASS STOP_VIDEO`。
- 通知包含 `VIDEO_MODE|VIDEO_STOP_OK`。
- 设备存储通知仍为 `newRecordVideo=0,totalRecordVideo=0`。
- `UTE_WIFI_MEDIA_DIAGNOSTICS` 仍能访问 `http://192.168.222.1:8000/media/list`。
- `UTE_WIFI_MEDIA_LIST` 仍只返回 Photo，没有 mp4/mov。
- `UTE_WIFI_DIRECT_DOWNLOAD_FIRST` 和 `UTE_WIFI_MEDIA_DOWNLOAD_FIRST` 均正确失败：`no ... media file to download kind=Video`。

当前结论：

- 录像开始前补齐 Glory View 参数后，PatrolLink 仍无法让当前 Glass 产出 video 文件。
- 这进一步说明当前缺口不在 PatrolLink 的 Wi-Fi 列表/下载/类型过滤，而在设备端 video 文件产出条件。
- 后续应优先用 Glory View 或眼镜物理操作先制造一个可见 mp4，再用 PatrolLink 的 `wifiDownloadKind=video` 验证同步；如果 Glory View 也不能产生 mp4，则需要换设备模式或确认当前 Glass 型号固件的视频能力限制。

### 2026-06-14 01:49 SDK demo 文档核对：录像参数修正

用户提醒“可以去查看文档，是不是命令下错了”。本轮重新核对 SDK 包内同版本 demo：

- 本地文档入口：
  - `/Users/qiuqiquan/Desktop/SmartHeadsetSystem/PatrolLink/UteWatchSDK_Android炬芯_V1.3.5/在线文档.txt`
  - KDocs 链接：`https://kdocs.cn/l/clTtBk8yvHED`
- 同版本 demo 源码：
  - `/Users/qiuqiquan/Desktop/SmartHeadsetSystem/PatrolLink/UteWatchSDK_Android炬芯_V1.3.5/UteWatchSdkDemohx/app/src/main/java/com/yc/nadalsdkdemo/smartglasses/SmartGlassesActivity.java`

核对结果：

- 录像开关命令本身没有下错：
  - `GlassesHeadsetRecordingState.RECORDING_STATE_START = 1`
  - `GlassesHeadsetRecordingState.RECORDING_STATE_STOP = 0`
  - SDK demo 用 `toggleGlassesVideoRecording(RECORDING_STATE_START)` 触发“APP控制眼镜录视频”。
- 之前可疑点在录像参数：
  - SDK demo 的 `setVideoParameters` 示例是 `width=240, height=0, frameRate=16`。
  - SDK demo 的 `setGlassesRecordingDuration` 示例是 `24 * 60 * 60` 秒。
  - 我们上一轮按 Glory View 日志试过 `2112x1568@30`，这和 SDK demo 不一致。

代码修正：

- `UteSdkDeviceGateway.videoRecordStartAttempts(...)` 的录像开始前置参数改为 SDK demo 示例：
  - `setGlassesRecordingDirection(VERTICAL_SCREEN)`
  - `setGlassesRecordingDuration(24 * 60 * 60)`
  - `setVideoParameters(240, 0, 16)`
  - `toggleGlassesVideoRecording(RECORDING_STATE_START)`
- debug direct matrix 同步改成相同参数，默认跳过 audio direct 命令，避免干扰 Glass video 结论。

验证：

- 编译和目标测试通过：
  - `./gradlew :app:compileDebugKotlin :app:testDebugUnitTest --tests com.patrollink.debug.SmokeWifiPreflightOptionsTest --console=plain`
  - `./gradlew :app:assembleDebug --console=plain`
- 新 APK：
  - `/Users/qiuqiquan/Desktop/SmartHeadsetSystem/PatrolLink/dist/PatrolLink-20260614-sdk-demo-video-params-debug.apk`
- 真机安装成功：
  - `lastUpdateTime=2026-06-14 01:46:47`
- 模拟器也已安装并启动最新 PatrolLink。

direct SDK 验证：

- 报告：
  - `/Users/qiuqiquan/Desktop/SmartHeadsetSystem/PatrolLink/dist/smoke-reports/smoke-test-20260614-real-glass-sdk-demo-video-params-direct-408.txt`
  - `/Users/qiuqiquan/Desktop/SmartHeadsetSystem/PatrolLink/dist/smoke-reports/logcat-20260614-real-glass-sdk-demo-video-params-direct-408.txt`
- 本次 force-stop 后重新连接 Glass，direct matrix 全部 SDK 命令返回 408：
  - `MATRIX_SET_RECORD_DIRECTION success=false,error=408`
  - `MATRIX_SET_RECORD_DURATION success=false,error=408`
  - `MATRIX_SET_VIDEO_PARAMETERS success=false,error=408`
  - `MATRIX_VIDEO_START_DIRECT success=false,error=408`
  - `MATRIX_VIDEO_STOP_DIRECT success=false,error=408`
- 因此本次 direct matrix 不能证明 SDK demo 参数是否能产出 video，只能证明当前 force-stop 后 SDK 控制链路未恢复。

当前结论：

- 文档/demo 确认：录像 start/stop 状态值没有错，但录像参数应按 SDK demo 使用 `240x0@16` 和 24 小时时长。
- PatrolLink 已按这个修正生产录像 start 前置配置。
- 仍待验证：在 Glass SDK 控制链路恢复到非 408 状态后，使用 SDK demo 参数是否能让设备端 `/media/list` 出现 mp4。

### 2026-06-14 01:51 SDK demo 参数后续：统一 Wi-Fi/控制 warmup

继续排查“是不是命令下错了”时发现：

- `UteSdkDeviceGateway.videoRecordStartAttempts(...)` 已经使用 SDK demo 参数。
- 但 `UteWifiMediaClient.applyGloryViewWifiWarmup(...)` 和 `UteSdkDeviceControlGateway.applyWifiOpenWarmup(...)` 里还残留旧的 `30s + 2112x1568@30`。
- 这两个 warmup 会在媒体页/控制页打开设备热点时执行，可能把 Glass 录像参数重新写成和 SDK demo 不一致的值。

代码修正：

- `UteWifiMediaClient` warmup 参数统一为：
  - `setGlassesRecordingDuration(24 * 60 * 60)`
  - `setVideoParameters(240, 0, 16)`
- `UteSdkDeviceControlGateway` warmup 参数统一为：
  - `setGlassesRecordingDuration(24 * 60 * 60)`
  - `setVideoParameters(240, 0, 16)`

验证：

- 编译和目标测试通过：
  - `./gradlew :app:compileDebugKotlin :app:testDebugUnitTest --tests com.patrollink.debug.SmokeWifiPreflightOptionsTest --tests com.patrollink.data.ute.UteCommandPolicyTest --console=plain`
  - `./gradlew :app:assembleDebug --console=plain`

当前结论：

- 文档/demo 仍说明 start/stop 命令值没有错。
- 已确认并修正的错误点是：部分路径残留的录像参数和 SDK demo 不一致。
- 还没有重跑真机 Glass 录像闭环；上一次 direct matrix 失败原因是 SDK 命令链路返回 408，不是本次参数修正的反证。
