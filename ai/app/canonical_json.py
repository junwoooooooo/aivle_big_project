"""Cross-language canonical JSON used by the internal task hash contract."""

from __future__ import annotations

import hashlib
import json
import math
import unicodedata
from decimal import Decimal
from typing import Any


def canonical_number(value: int | float | Decimal) -> str:
    if isinstance(value, bool):
        raise TypeError("boolean is not a JSON number")
    if isinstance(value, float):
        if not math.isfinite(value):
            raise ValueError("non-finite JSON number is not canonical task input")
        decimal = Decimal(str(value))
    else:
        decimal = Decimal(value)
    if not decimal.is_finite():
        raise ValueError("non-finite JSON number is not canonical task input")
    if decimal.is_zero():
        return "0"
    return format(decimal.normalize(), "f")


def canonical_json(value: Any) -> str:
    if value is None:
        return "null"
    if isinstance(value, bool):
        return "true" if value else "false"
    if isinstance(value, (int, float, Decimal)):
        return canonical_number(value)
    if isinstance(value, str):
        return json.dumps(unicodedata.normalize("NFC", value), ensure_ascii=False)
    if isinstance(value, (list, tuple)):
        return "[" + ",".join(canonical_json(item) for item in value) + "]"
    if isinstance(value, dict):
        normalized: dict[str, Any] = {}
        for key, item in value.items():
            if not isinstance(key, str):
                raise TypeError("canonical JSON object keys must be strings")
            normalized_key = unicodedata.normalize("NFC", key)
            if normalized_key in normalized:
                raise ValueError("normalized key collision")
            normalized[normalized_key] = item
        return "{" + ",".join(
            canonical_json(key) + ":" + canonical_json(normalized[key])
            for key in sorted(normalized)
        ) + "}"
    raise TypeError(f"unsupported canonical JSON value: {type(value).__name__}")


def canonical_input_hash(*, contract_version: str, task_type: str,
                         task_schema_version: str, locale: str,
                         input_value: dict[str, Any]) -> str:
    envelope = {
        "contractVersion": contract_version,
        "taskType": task_type,
        "taskSchemaVersion": task_schema_version,
        "locale": locale,
        "input": input_value,
    }
    encoded = canonical_json(envelope).encode("utf-8")
    return "sha256:" + hashlib.sha256(encoded).hexdigest()
