import os
import uuid

from flask import Flask, jsonify, request

from auth import issue_token, require_auth
from config import Settings
from database import BridgeDB
from runner import TaskRunner


app = Flask(__name__)
app.config["SECRET_KEY"] = Settings.SECRET_KEY
app.config["TOKEN_MAX_AGE_SECONDS"] = Settings.TOKEN_MAX_AGE_SECONDS


db_path = Settings.DB_PATH
if not os.path.isabs(db_path):
    db_path = os.path.join(os.path.dirname(__file__), db_path)

db = BridgeDB(db_path)
runner = TaskRunner(db, Settings.CODEX_COMMAND_TEMPLATE, Settings.DEFAULT_WORKDIR)


@app.get("/api/health")
def health_api():
    return jsonify({"ok": True})


@app.post("/api/auth/login")
def login_api():
    data = request.get_json(silent=True) or {}
    password = str(data.get("password", ""))

    if password != Settings.PASSWORD:
        return jsonify({"error": "Invalid password."}), 401

    token = issue_token()
    return jsonify({"token": token})


@app.get("/api/config")
@require_auth
def config_api():
    return jsonify(
        {
            "default_workdir": Settings.DEFAULT_WORKDIR,
            "command_template": Settings.CODEX_COMMAND_TEMPLATE,
            "token_ttl_seconds": Settings.TOKEN_MAX_AGE_SECONDS,
        }
    )


@app.post("/api/tasks")
@require_auth
def create_task_api():
    data = request.get_json(silent=True) or {}
    prompt = str(data.get("prompt", "")).strip()
    cwd = str(data.get("cwd", "")).strip() or None

    if not prompt:
        return jsonify({"error": "prompt is required."}), 400

    task_id = uuid.uuid4().hex
    db.create_task(task_id, prompt, cwd)
    runner.submit(task_id, prompt, cwd)

    task = db.get_task(task_id)
    return jsonify({"task": task}), 201


@app.get("/api/tasks")
@require_auth
def list_tasks_api():
    limit = request.args.get("limit", default=50, type=int)
    limit = max(1, min(limit, 200))
    items = db.list_tasks(limit=limit)
    return jsonify({"items": items})


@app.get("/api/tasks/<task_id>")
@require_auth
def get_task_api(task_id):
    task = db.get_task(task_id)
    if not task:
        return jsonify({"error": "Task not found."}), 404

    return jsonify({"task": task})


@app.get("/api/tasks/<task_id>/logs")
@require_auth
def get_task_logs_api(task_id):
    after = request.args.get("after", default=0, type=int)
    limit = request.args.get("limit", default=200, type=int)
    limit = max(1, min(limit, 1000))

    task = db.get_task(task_id)
    if not task:
        return jsonify({"error": "Task not found."}), 404

    logs = db.get_logs(task_id, after_seq=after, limit=limit)
    latest_seq = logs[-1]["seq"] if logs else after

    return jsonify({"logs": logs, "latest_seq": latest_seq, "task_status": task["status"]})


@app.post("/api/tasks/<task_id>/cancel")
@require_auth
def cancel_task_api(task_id):
    task = db.get_task(task_id)
    if not task:
        return jsonify({"error": "Task not found."}), 404

    if task["status"] not in ("queued", "running"):
        return jsonify({"error": f"Task already {task['status']}."}), 400

    ok = runner.cancel(task_id)
    if not ok:
        return jsonify({"error": "Task is not currently running."}), 400

    db.append_log(task_id, "[bridge] Cancel requested by mobile client.")
    db.update_task_status(task_id, "cancelled")
    return jsonify({"message": "Cancel signal sent."})


if __name__ == "__main__":
    app.run(host=Settings.HOST, port=Settings.PORT, debug=True)
