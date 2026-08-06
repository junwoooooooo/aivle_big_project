from uuid import uuid4

from fastapi import Request


REQUEST_ID_HEADER = "X-Request-Id"


def resolve_request_id(value: str | None) -> str:
    if value is not None and value.strip():
        return value.strip()
    return str(uuid4())


def current_request_id(request: Request) -> str:
    value = getattr(request.state, "request_id", None)
    if value:
        return value
    value = resolve_request_id(request.headers.get(REQUEST_ID_HEADER))
    request.state.request_id = value
    return value
