# Smart Home Project Setup Tutorial (macOS + Windows)

This guide is for a SET-UP in a fresh environment. (Web Simulator + Safety Worker + Android app).

## 1. What this project contains

- mobile-client: Android app (Kotlin + Jetpack Compose)
- web-simulator: Next.js web dashboard
- safety-worker: Node.js backend worker for auto-cutoff safety logic

All three parts connect to the same Firebase Realtime Database.

## 2. Prerequisites

Install these first.

### macOS

1. Install Git
- https://git-scm.com/downloads

2. Install Node.js LTS (recommended: 20.x or newer)
- https://nodejs.org/

3. Install Java 17 (required for Android build)
- Temurin 17: https://adoptium.net/

4. Install Android Studio (latest stable)
- https://developer.android.com/studio
- During first launch, install:
  - Android SDK Platform 37
  - Android SDK Build-Tools
  - Android Emulator (optional but recommended)

### Windows

1. Install Git for Windows
- https://git-scm.com/downloads

2. Install Node.js LTS (recommended: 20.x or newer)
- https://nodejs.org/

3. Install Java 17 (Temurin recommended)
- https://adoptium.net/

4. Install Android Studio (latest stable)
- https://developer.android.com/studio
- During first launch, install:
  - Android SDK Platform 37
  - Android SDK Build-Tools
  - Android Emulator (optional)

## 3. Clone the repository

Use terminal (macOS) or PowerShell (Windows):

```bash
git clone https://github.com/ansinayke/Smart_Home_Eco_System.git
cd Smart_Home_Eco_System
```

## 4. Firebase credentials and environment setup

This project needs Firebase credentials for web and worker, plus Android Firebase config.

### Web simulator environment file

Create this file:

- web-simulator/.env.local

Paste these variables:

```env
NEXT_PUBLIC_FIREBASE_API_KEY=your_value
NEXT_PUBLIC_FIREBASE_AUTH_DOMAIN=your_value
NEXT_PUBLIC_FIREBASE_DATABASE_URL=your_value
NEXT_PUBLIC_FIREBASE_PROJECT_ID=your_value
NEXT_PUBLIC_FIREBASE_STORAGE_BUCKET=your_value
NEXT_PUBLIC_FIREBASE_MESSAGING_SENDER_ID=your_value
NEXT_PUBLIC_FIREBASE_APP_ID=your_value
```

Get these values from Firebase Console:
- Project Settings -> General -> Your apps -> Web app config

### Safety worker service account key

File needed:

- safety-worker/serviceAccountKey.json

If it is not already present after cloning, ask the project owner for it.

How to generate (project owner):
- Firebase Console -> Project Settings -> Service Accounts
- Click "Generate new private key"
- Save as serviceAccountKey.json inside safety-worker/

### Android google-services file

File needed:

- mobile-client/app/google-services.json

If missing, download it from:
- Firebase Console -> Project Settings -> General -> Your Android app

Then place it in mobile-client/app/. -->

## 5. Install dependencies

### 5.1 Safety worker

```bash
cd safety-worker
npm install
cd ..
```

### 5.2 Web simulator

```bash
cd web-simulator
npm install
cd ..
```

## 6. Seed database (first run)

Run this once to populate initial house/floor/device data.

```bash
cd safety-worker
npm run seed
cd ..
```

If successful, it prints a database seeded message.

## 7. Start all services

Use three separate terminal windows/tabs.

### Terminal 1: Safety worker

```bash
cd safety-worker
npm start
```

Expected: logs saying it is listening on house/floors.

### Terminal 2: Web simulator

```bash
cd web-simulator
npm run dev
```

Open:
- http://localhost:3000

### The Android app

Option A : Open the app on your emulator phone(Make sure your PC is connected to the internet)

Option B : Open the app on your physical phone(The phone needs an internet connection)

## 8. Verify everything works

1. Web dashboard loads and shows floors/devices.
2. Toggle a SMART_SWITCH to ON in web.
3. Safety worker logs that timer is armed.
4. After max duration, worker auto-turns it OFF and writes:
- usage_logs
- alerts
5. Android app reflects updated state from Firebase.

## Android local.properties note

The file mobile-client/local.properties is machine-specific. Android Studio usually creates/fixes it automatically.

If needed, set sdk.dir to your SDK path.

Examples:

macOS:
```properties
sdk.dir=/Users/<your-user>/Library/Android/sdk
```

Windows:
```properties
sdk.dir=C:\\Users\\<your-user>\\AppData\\Local\\Android\\Sdk -->
```

## 9. Common issues and fixes

### Node command not found
- Reinstall Node.js LTS
- Restart terminal
- Check with: node -v and npm -v

### Port 3000 already in use
- Stop conflicting process, or run web on another port:
```bash
cd web-simulator
npm run dev -- -p 3001
```

### Firebase permission / auth errors
- Confirm serviceAccountKey.json is valid and matches the same Firebase project.
- Confirm .env.local values point to the same project.

### Android Gradle sync fails
- Ensure Java 17 is installed and selected in Android Studio.
- Ensure Android SDK 37 is installed.
- Re-sync Gradle and rebuild.

### google-services.json missing
- Place the file in mobile-client/app/ and sync again.

## 10. Recommended startup order

1. Run seed (first time only)
2. Start safety-worker
3. Start web-simulator
4. Launch mobile-client

This ensures safety logic is active while clients are being used.
