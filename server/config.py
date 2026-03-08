import os


class Settings:
    HOST = os.getenv("BRIDGE_HOST", "0.0.0.0")
    PORT = int(os.getenv("BRIDGE_PORT", "8765"))

    SECRET_KEY = os.getenv("BRIDGE_SECRET_KEY", "change-this-secret-key")
    PASSWORD = os.getenv("BRIDGE_PASSWORD", "change-this-password")

    DB_PATH = os.getenv("BRIDGE_DB_PATH", "bridge.db")

    CODEX_COMMAND_TEMPLATE = os.getenv(
        "CODEX_COMMAND_TEMPLATE",
        "codex exec {prompt}",
    )
    CODEX_RESUME_COMMAND_TEMPLATE = os.getenv(
        "CODEX_RESUME_COMMAND_TEMPLATE",
        "codex exec resume --skip-git-repo-check --full-auto {session_id} {prompt}",
    )
    DEFAULT_WORKDIR = os.getenv("BRIDGE_DEFAULT_WORKDIR", "")

    TOKEN_MAX_AGE_SECONDS = int(os.getenv("BRIDGE_TOKEN_MAX_AGE_SECONDS", "604800"))
