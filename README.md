# PL2Android

Android native implementation for the PatrolLink law-enforcement headset app.

Stack:

- Kotlin
- Jetpack Compose + Material 3
- MVVM style state holder
- Production REST, Cerebellum REST, SourceNex/UTE/BLE, and Wi-Fi transfer integrations

Open this folder in Android Studio and run the `app` configuration.

## Implemented Feature Scope

- Real backend login, token refresh, session restore, and secure logout.
- Device scan, bind, photo command, recording toggle and intercom toggle.
- Alert observe, acknowledge, close, false alarm and backup handling paths.
- Media list, SHA-256 verification state, download/upload progress state machine and delete.
- 15-second heartbeat/location updates, command polling/ACK, and message/alert catch-up.
- Stream relay state machine for low-latency/balanced/evidence-quality modes; live headset video still depends on vendor SDK support.
- SOS activation/cancel flow with location, audio recording and backup ETA state.
- Spring Boot REST contracts using `code/message/data/traceId/timestamp`.
- Paged list contracts using `items/page/pageSize/total/hasMore`.
- Platform boundaries for secure token storage, Android permission planning, background task queueing and evidence integrity hashing.
- OkHttp-based real REST client and REST-backed gateway implementations.
- OkHttp WebSocket realtime gateway skeleton for heartbeat and alert push transport.
- Android BLE scan gateway skeleton and BLE command codec.
- Wi-Fi file service client for device hotspot file listing, download and upload.
- Android Keystore encrypted session store.
- Runtime permission gate for BLE/location/camera/audio/notification permissions.
- Foreground service and notification channel for SOS, streaming, intercom and heartbeat keep-alive.
- Network monitor based on `ConnectivityManager`.
- Version check gateway and offline sync engine.

The hardware/network boundaries are defined as Kotlin interfaces in `domain/Contracts.kt`.
Production uses real dependencies from `RuntimeDependencyFactory`; mocks live only in unit-test sources. Missing backend or hardware channels fail explicitly instead of reporting simulated success.

## REST Data Contract

The Android client follows the Spring Boot backend contract. Backend storage choices such as Redis, MySQL, or a domestic database remain server-side details; the app depends only on stable REST DTOs. Test mocks use the same contract for retry and mapping tests.

Response envelope:

```json
{
  "code": 200,
  "message": "OK",
  "data": {},
  "traceId": "trace-example-0001",
  "timestamp": 1715832000
}
```

Paged response:

```json
{
  "items": [],
  "page": 1,
  "pageSize": 20,
  "total": 0,
  "hasMore": false
}
```

DTOs and mappers live under `app/src/main/java/com/patrollink/data/remote`.

## Real Integration Switch Points

- Backend REST: `data/remote/OkHttpPatrolRestApi.kt`
- Cerebellum edge REST: `data/edge/OkHttpCerebellumApi.kt`
- Backend-backed gateways: `data/RestBackedGateways.kt`
- WebSocket: `data/realtime/OkHttpWebSocketRealtimeGateway.kt`
- BLE: `data/ble/AndroidBleDeviceGateway.kt`
- Wi-Fi file transfer: `data/file/WifiFileServiceClient.kt`
- Secure session storage: `data/local/AndroidKeystoreSecureStore.kt`
- Offline task persistence: `data/local/JsonFileBackgroundTaskGateway.kt`
- Foreground keep-alive: `service/PatrolForegroundService.kt`

Cerebellum direct connection can be configured with build properties or environment variables:

```bash
PATROL_CEREBELLUM_BASE_URL=http://127.0.0.1:8088 \
PATROL_CEREBELLUM_API_KEY=change-this-key \
./gradlew assembleDebug
```

The mobile runtime config is also packaged as JSON so normal builds do not depend on remembering environment variables:

- Development: `app/src/debug/assets/patrol-runtime.json`
- Production: `app/src/release/assets/patrol-runtime.json`

Config precedence is: settings saved on the device > the build-type `patrol-runtime.json` > Gradle `BuildConfig` / environment variable fallback. The debug package defaults to `http://10.0.2.2:8080` for the host backend from the Android emulator.

Use `http://10.0.2.2:8088` from the Android emulator to reach a Docker service on the host. For field devices, use the hotspot or Wi-Fi Direct `192.168.x.x` address; cross-network access should go through HTTPS/mTLS.

Remaining production work requires real backend URLs/contracts beyond the current DTOs, headset GATT UUIDs, command acknowledgement protocol, Wi-Fi hotspot file API details, cerebellum discovery/pairing, and streaming SDK endpoints.

## Verification

```bash
./gradlew clean testDebugUnitTest assembleDebug
```

Smoke result from this workspace: build successful, all JVM unit tests passed.
No emulator/device was attached, so install-and-launch smoke testing was not run.
