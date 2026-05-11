# PL2Android

Android native implementation for the PatrolLink law-enforcement headset app.

Stack:

- Kotlin
- Jetpack Compose + Material 3
- MVVM style state holder
- Mock repository shaped like the future REST/WebSocket/BLE data contracts

Open this folder in Android Studio and run the `app` configuration.

## Implemented Feature Scope

- Login/session startup with token-shaped mock response.
- Device scan, bind, photo command, recording toggle and intercom toggle.
- Alert observe, acknowledge, close, false alarm and backup handling paths.
- Media list, SHA-256 verification state, download/upload progress state machine and delete.
- WebSocket-style connection and heartbeat acknowledgement.
- Stream relay state machine for low-latency/balanced/evidence-quality modes.
- SOS activation/cancel flow with location, audio recording and backup ETA state.
- Spring Boot style REST mock contracts using `code/message/data/traceId/timestamp`.
- Paged list contracts using `items/page/pageSize/total/hasMore`.
- Platform boundaries for secure token storage, Android permission planning, background task queueing and evidence integrity hashing.

The hardware/network boundaries are defined as Kotlin interfaces in `domain/Contracts.kt`.
Current implementations are deterministic mocks so the app can run without headset hardware or backend endpoints.

## Mock REST Data Contract

The Android mock layer is shaped to match a future Spring Boot backend. Backend storage choices such as Redis cache, MySQL, or a domestic database remain server-side implementation details; the app only depends on stable REST response DTOs.

Response envelope:

```json
{
  "code": 200,
  "message": "OK",
  "data": {},
  "traceId": "mock-trace-0001",
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

## Verification

```bash
./gradlew clean testDebugUnitTest assembleDebug
```

Smoke result from this workspace: build successful, all JVM unit tests passed.
No emulator/device was attached, so install-and-launch smoke testing was not run.
