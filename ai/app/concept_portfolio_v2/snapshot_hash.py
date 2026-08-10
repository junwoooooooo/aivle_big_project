"""Java SnapshotHasher와 같은 canonical JSON 기반 hash."""

from __future__ import annotations

import hashlib
import json
from typing import Any

from pydantic import BaseModel

from app.canonical_json import canonical_json


def production_compatible_snapshot_hash(value: Any) -> str:
    if isinstance(value, BaseModel):
        value = value.model_dump(mode="json")
    else:
        # Pydantic models can also be nested inside ordinary mappings.  A JSON
        # round-trip through Pydantic's encoder produces the same primitive
        # value domain consumed by the Java SnapshotHasher.
        value = json.loads(json.dumps(value, default=lambda item: item.model_dump(mode="json")))
    return "sha256:" + hashlib.sha256(canonical_json(value).encode("utf-8")).hexdigest()
