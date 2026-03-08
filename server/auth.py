from functools import wraps

from flask import current_app, jsonify, request
from itsdangerous import BadSignature, SignatureExpired, URLSafeTimedSerializer


def _serializer() -> URLSafeTimedSerializer:
    return URLSafeTimedSerializer(current_app.config["SECRET_KEY"], salt="codex-bridge")


def issue_token() -> str:
    return _serializer().dumps({"role": "mobile_client"})


def verify_token(token: str) -> bool:
    try:
        _serializer().loads(token, max_age=current_app.config["TOKEN_MAX_AGE_SECONDS"])
        return True
    except (BadSignature, SignatureExpired):
        return False


def require_auth(view_func):
    @wraps(view_func)
    def wrapper(*args, **kwargs):
        auth_header = request.headers.get("Authorization", "")
        if not auth_header.startswith("Bearer "):
            return jsonify({"error": "Missing bearer token."}), 401

        token = auth_header.replace("Bearer ", "", 1).strip()
        if not verify_token(token):
            return jsonify({"error": "Invalid or expired token."}), 401

        return view_func(*args, **kwargs)

    return wrapper
