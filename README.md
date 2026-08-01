# LingoLens

LingoLens is a graduation-project English learning platform. The Android app recognizes everyday objects, teaches contextual vocabulary, and synchronizes learning progress through a secured Spring Boot API.

## Architecture

- Android: Kotlin, Jetpack Compose, Retrofit, OkHttp, DataStore and Android Keystore
- Backend: Kotlin, Spring Boot, Spring Security, JWT, Flyway
- Database: PostgreSQL
- Admin web: planned React/TypeScript dashboard

## Run PostgreSQL

```powershell
docker compose up -d postgres
```

Copy `.env.example` to `.env` and replace development credentials before deployment.

## Run backend

```powershell
cd backend
.\gradlew.bat bootRun
```

- API: `http://localhost:8080`
- Swagger: `http://localhost:8080/swagger-ui.html`
- Health: `http://localhost:8080/actuator/health`

## Run Android

Start the backend first, then run the Android app on an emulator. The development base URL is `http://10.0.2.2:8080/`, which maps from the emulator to the host computer.

```powershell
.\gradlew.bat assembleDebug
```

For a physical device, change the development base URL to the computer's LAN address and keep both devices on the same network. Production builds must use HTTPS.

## Verification

```powershell
cd backend
.\gradlew.bat test
cd ..
.\gradlew.bat testDebugUnitTest assembleDebug
```

Secrets, `.env`, local configuration and build outputs are excluded from Git.
