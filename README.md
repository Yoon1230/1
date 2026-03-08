# Codex Mobile Bridge (Android + PC)

This project lets an Android app directly control Codex running on your PC (not remote desktop).

Architecture:
- `server/`: Flask bridge service on your PC.
- `android/`: Native Android app (Jetpack Compose) that talks to the bridge API.

## 1) PC bridge service

### Files
- `server/app.py`
- `server/config.py`
- `server/database.py`
- `server/runner.py`

### Main environment variables
- `BRIDGE_PASSWORD`: password used by Android login.
- `BRIDGE_SECRET_KEY`: token signing key.
- `BRIDGE_HOST`: default `0.0.0.0`
- `BRIDGE_PORT`: default `8765`
- `CODEX_COMMAND_TEMPLATE`: default `codex exec {prompt}`
- `CODEX_RESUME_COMMAND_TEMPLATE`: default `codex exec resume --skip-git-repo-check --full-auto {session_id} {prompt}`
- `BRIDGE_DEFAULT_WORKDIR`: optional default working directory.

### Run
```bash
cd server
pip install -r requirements.txt
set BRIDGE_PASSWORD=your-password
python app.py
```

Then keep this PC service running.

## 2) Android app

Import `android/` into Android Studio.

In app login page, set bridge URL like:
- `http://<your-pc-lan-ip>:8765/`

Then login with `BRIDGE_PASSWORD` and start chatting.

## API summary
- `POST /api/auth/login`
- `POST /api/chat/new`
- `POST /api/chat/send`
- `GET /api/chat/messages?conversation_id=...`

Compatible legacy task APIs are still available:
- `GET /api/tasks`
- `POST /api/tasks`
- `GET /api/tasks/{id}`
- `GET /api/tasks/{id}/logs?after=0`
- `POST /api/tasks/{id}/cancel`

## Security notes
- Use a strong `BRIDGE_PASSWORD` and `BRIDGE_SECRET_KEY`.
- Keep bridge in LAN only; do not expose directly to public internet without HTTPS/reverse proxy.
- If exposed externally, add IP allowlist and TLS.

## 3) Build APK with GitHub Actions (No Android Studio)

1. Push this repository to GitHub.
2. Open GitHub `Actions` tab.
3. Run workflow: `Build Android APK` (or push to `main/master` with Android changes).
4. After it finishes, open the run and download artifact: `codex-bridge-app-debug`.

Artifact includes:
- `app-debug.apk`

Notes:
- Workflow installs Android SDK + Build Tools automatically.
- Build uses Gradle 8.10.2 and JDK 17.
