import sqlite3
import threading
from datetime import datetime


class BridgeDB:
    def __init__(self, db_path: str):
        self.db_path = db_path
        self._lock = threading.Lock()
        self._init_db()

    def _connect(self):
        conn = sqlite3.connect(self.db_path, check_same_thread=False)
        conn.row_factory = sqlite3.Row
        return conn

    def _init_db(self):
        with self._connect() as conn:
            conn.executescript(
                """
                CREATE TABLE IF NOT EXISTS tasks (
                    id TEXT PRIMARY KEY,
                    prompt TEXT NOT NULL,
                    status TEXT NOT NULL,
                    cwd TEXT,
                    conversation_id TEXT,
                    session_id TEXT,
                    created_at TEXT NOT NULL,
                    started_at TEXT,
                    finished_at TEXT,
                    exit_code INTEGER,
                    error_message TEXT
                );

                CREATE TABLE IF NOT EXISTS task_logs (
                    id INTEGER PRIMARY KEY AUTOINCREMENT,
                    task_id TEXT NOT NULL,
                    seq INTEGER NOT NULL,
                    line TEXT NOT NULL,
                    created_at TEXT NOT NULL,
                    FOREIGN KEY(task_id) REFERENCES tasks(id)
                );
                """
            )

            task_columns = {
                r["name"] for r in conn.execute("PRAGMA table_info(tasks)").fetchall()
            }
            if "conversation_id" not in task_columns:
                conn.execute("ALTER TABLE tasks ADD COLUMN conversation_id TEXT")
            if "session_id" not in task_columns:
                conn.execute("ALTER TABLE tasks ADD COLUMN session_id TEXT")

            conn.executescript(
                """
                CREATE INDEX IF NOT EXISTS idx_task_logs_task_seq ON task_logs(task_id, seq);
                CREATE INDEX IF NOT EXISTS idx_tasks_created_at ON tasks(created_at DESC);
                CREATE INDEX IF NOT EXISTS idx_tasks_conversation_created ON tasks(conversation_id, created_at);
                """
            )
            conn.commit()

    def create_task(
        self,
        task_id: str,
        prompt: str,
        cwd: str | None,
        *,
        conversation_id: str | None = None,
        session_id: str | None = None,
    ):
        now = datetime.utcnow().isoformat()
        with self._lock:
            with self._connect() as conn:
                conn.execute(
                    """
                    INSERT INTO tasks (id, prompt, status, cwd, conversation_id, session_id, created_at)
                    VALUES (?, ?, ?, ?, ?, ?, ?)
                    """,
                    (task_id, prompt, "queued", cwd, conversation_id, session_id, now),
                )
                conn.commit()

    def update_task_status(
        self,
        task_id: str,
        status: str,
        *,
        started_at: str | None = None,
        finished_at: str | None = None,
        exit_code: int | None = None,
        error_message: str | None = None,
    ):
        with self._lock:
            with self._connect() as conn:
                conn.execute(
                    """
                    UPDATE tasks
                    SET status = ?,
                        started_at = COALESCE(?, started_at),
                        finished_at = COALESCE(?, finished_at),
                        exit_code = COALESCE(?, exit_code),
                        error_message = COALESCE(?, error_message)
                    WHERE id = ?
                    """,
                    (
                        status,
                        started_at,
                        finished_at,
                        exit_code,
                        error_message,
                        task_id,
                    ),
                )
                conn.commit()

    def set_task_session_id(self, task_id: str, session_id: str):
        with self._lock:
            with self._connect() as conn:
                conn.execute(
                    "UPDATE tasks SET session_id = ? WHERE id = ?",
                    (session_id, task_id),
                )
                conn.commit()

    def append_log(self, task_id: str, line: str):
        now = datetime.utcnow().isoformat()
        with self._lock:
            with self._connect() as conn:
                seq_row = conn.execute(
                    "SELECT COALESCE(MAX(seq), 0) + 1 AS next_seq FROM task_logs WHERE task_id = ?",
                    (task_id,),
                ).fetchone()
                seq = int(seq_row["next_seq"])

                conn.execute(
                    """
                    INSERT INTO task_logs (task_id, seq, line, created_at)
                    VALUES (?, ?, ?, ?)
                    """,
                    (task_id, seq, line[:4000], now),
                )
                conn.commit()
                return seq

    def get_task(self, task_id: str):
        with self._connect() as conn:
            row = conn.execute("SELECT * FROM tasks WHERE id = ?", (task_id,)).fetchone()
            return dict(row) if row else None

    def list_tasks(self, limit: int = 50):
        with self._connect() as conn:
            rows = conn.execute(
                "SELECT * FROM tasks ORDER BY created_at DESC LIMIT ?",
                (limit,),
            ).fetchall()
            return [dict(r) for r in rows]

    def list_tasks_by_conversation(self, conversation_id: str, limit: int = 100):
        with self._connect() as conn:
            rows = conn.execute(
                """
                SELECT *
                FROM tasks
                WHERE conversation_id = ?
                ORDER BY created_at ASC
                LIMIT ?
                """,
                (conversation_id, limit),
            ).fetchall()
            return [dict(r) for r in rows]

    def latest_session_id(self, conversation_id: str) -> str | None:
        with self._connect() as conn:
            row = conn.execute(
                """
                SELECT session_id
                FROM tasks
                WHERE conversation_id = ?
                  AND session_id IS NOT NULL
                  AND session_id != ''
                ORDER BY created_at DESC
                LIMIT 1
                """,
                (conversation_id,),
            ).fetchone()
            return str(row["session_id"]) if row and row["session_id"] else None

    def get_logs(self, task_id: str, after_seq: int = 0, limit: int = 400):
        with self._connect() as conn:
            rows = conn.execute(
                """
                SELECT task_id, seq, line, created_at
                FROM task_logs
                WHERE task_id = ? AND seq > ?
                ORDER BY seq ASC
                LIMIT ?
                """,
                (task_id, after_seq, limit),
            ).fetchall()
            return [dict(r) for r in rows]

    def get_all_logs(self, task_id: str, limit: int = 2000):
        with self._connect() as conn:
            rows = conn.execute(
                """
                SELECT task_id, seq, line, created_at
                FROM task_logs
                WHERE task_id = ?
                ORDER BY seq ASC
                LIMIT ?
                """,
                (task_id, limit),
            ).fetchall()
            return [dict(r) for r in rows]
