import os
import subprocess
import threading
from datetime import datetime


class TaskRunner:
    def __init__(self, db, command_template: str, default_workdir: str = ""):
        self.db = db
        self.command_template = command_template
        self.default_workdir = default_workdir
        self.running_processes = {}
        self._lock = threading.Lock()

    def _render_command(self, prompt: str) -> str:
        safe_prompt = prompt.replace("\"", "\\\"").replace("\n", " ")
        template = self.command_template
        if "{prompt}" not in template:
            template = f"{template} {{prompt}}"
        return template.replace("{prompt}", f"\"{safe_prompt}\"")

    def submit(self, task_id: str, prompt: str, cwd: str | None = None):
        thread = threading.Thread(
            target=self._run_task,
            args=(task_id, prompt, cwd),
            daemon=True,
        )
        thread.start()

    def cancel(self, task_id: str) -> bool:
        with self._lock:
            proc = self.running_processes.get(task_id)
            if not proc:
                return False
            try:
                proc.terminate()
                return True
            except Exception:
                return False

    def _run_task(self, task_id: str, prompt: str, cwd: str | None = None):
        start_time = datetime.utcnow().isoformat()
        self.db.update_task_status(task_id, "running", started_at=start_time)

        command = self._render_command(prompt)
        run_dir = cwd or self.default_workdir or None

        if run_dir and not os.path.isdir(run_dir):
            self.db.append_log(task_id, f"[bridge] Invalid cwd: {run_dir}")
            self.db.update_task_status(
                task_id,
                "failed",
                finished_at=datetime.utcnow().isoformat(),
                exit_code=1,
                error_message=f"Invalid cwd: {run_dir}",
            )
            return

        self.db.append_log(task_id, f"[bridge] Running command: {command}")
        if run_dir:
            self.db.append_log(task_id, f"[bridge] Workdir: {run_dir}")

        try:
            proc = subprocess.Popen(
                command,
                shell=True,
                cwd=run_dir,
                stdout=subprocess.PIPE,
                stderr=subprocess.STDOUT,
                text=True,
                encoding="utf-8",
                errors="replace",
                bufsize=1,
            )

            with self._lock:
                self.running_processes[task_id] = proc

            if proc.stdout is not None:
                for line in iter(proc.stdout.readline, ""):
                    text = line.rstrip("\r\n")
                    if text:
                        self.db.append_log(task_id, text)

            proc.wait()
            code = int(proc.returncode or 0)

            current = self.db.get_task(task_id) or {}
            if current.get("status") == "cancelled":
                self.db.update_task_status(
                    task_id,
                    "cancelled",
                    finished_at=datetime.utcnow().isoformat(),
                    exit_code=code,
                    error_message="Cancelled by mobile client",
                )
            else:
                status = "completed" if code == 0 else "failed"
                err = None if code == 0 else f"Process exited with code {code}"
                self.db.update_task_status(
                    task_id,
                    status,
                    finished_at=datetime.utcnow().isoformat(),
                    exit_code=code,
                    error_message=err,
                )

        except Exception as exc:
            self.db.append_log(task_id, f"[bridge] Runner error: {exc}")
            self.db.update_task_status(
                task_id,
                "failed",
                finished_at=datetime.utcnow().isoformat(),
                exit_code=1,
                error_message=str(exc),
            )

        finally:
            with self._lock:
                self.running_processes.pop(task_id, None)
