"""Durable transport for Product Research2 recollect ledgers.

The donor engine continues to read and write its normal run directories. This
module only bundles the three files that ``research2.run._restore`` reads and
moves that bundle through the backend/ObjectStorage authority.
"""
from __future__ import annotations

import hashlib
import io
import json
import os
import shutil
import zipfile
import uuid
from datetime import datetime, timezone
from pathlib import Path

import httpx


CONTRACT_VERSION = "market-ledger.bundle.v1"
CONTENT_TYPE = "application/vnd.aivle.market-ledger+zip"
LEDGER_FILES = ("a3_bodies.json", "result.json", "run.jsonl")
MAX_BUNDLE_BYTES = 32 * 1024 * 1024
MAX_FILE_BYTES = 24 * 1024 * 1024


class MarketLedgerArtifactError(RuntimeError):
    pass


def _canonical_json(value: dict) -> bytes:
    return json.dumps(value, ensure_ascii=False, sort_keys=True,
                      separators=(",", ":")).encode("utf-8")


def _sha256(value: bytes) -> str:
    return hashlib.sha256(value).hexdigest()


def _runtime_binding(task_input: dict, diagnostic_context: dict[str, str]) -> dict:
    source = task_input.get("source") or {}
    required = {
        "projectId": source.get("projectId"),
        "conceptId": task_input.get("conceptId"),
        "conceptSnapshotHash": source.get("selectedConceptHash"),
        "canonicalInputHash": diagnostic_context.get("canonicalInputHash"),
        "marketTaskRunId": diagnostic_context.get("taskRunId"),
        "taskAttemptId": diagnostic_context.get("taskAttemptId"),
        "asOf": task_input.get("asOf"),
    }
    if not isinstance(required["projectId"], int) or any(
        not isinstance(value, str) or not value.strip()
        for key, value in required.items() if key != "projectId"
    ):
        raise MarketLedgerArtifactError("market ledger runtime binding is incomplete")
    return required


def _backend_config() -> tuple[str, str]:
    base_url = os.getenv("BACKEND_INTERNAL_BASE_URL", "").strip().rstrip("/")
    token = os.getenv("AI_INTERNAL_SERVICE_TOKEN", "").strip()
    if not base_url or not token:
        raise MarketLedgerArtifactError("market ledger backend contract is not configured")
    return base_url, token


def _read_ledger(directory: Path) -> dict[str, bytes]:
    values: dict[str, bytes] = {}
    for name in LEDGER_FILES:
        path = directory / name
        if not path.is_file():
            raise MarketLedgerArtifactError(f"market ledger file is missing: {name}")
        content = path.read_bytes()
        if not content or len(content) > MAX_FILE_BYTES:
            raise MarketLedgerArtifactError(f"market ledger file size is invalid: {name}")
        values[name] = content
    return values


def build_bundle(task_input: dict, diagnostic_context: dict[str, str],
                 workspace: str, source_run_id: str) -> tuple[bytes, dict]:
    binding = _runtime_binding(task_input, diagnostic_context)
    generated = Path(workspace, "runs-generated", source_run_id)
    seed = Path(workspace, "runs", source_run_id)
    directory = generated if generated.is_dir() else seed
    files = _read_ledger(directory)
    manifest = {
        "artifactContractVersion": CONTRACT_VERSION,
        **binding,
        "sourceRunId": source_run_id,
        "sourceMarketResearchVersionId": (task_input.get("ledgerArtifact") or {}).get(
            "sourceMarketResearchVersionId"),
        "createdAt": datetime.now(timezone.utc).isoformat().replace("+00:00", "Z"),
        "engineIdentifier": "research2-main-ad730475",
        "files": [
            {"name": name, "sizeBytes": len(files[name]), "sha256": _sha256(files[name])}
            for name in LEDGER_FILES
        ],
    }
    manifest["manifestHash"] = _sha256(_canonical_json(manifest))
    output = io.BytesIO()
    with zipfile.ZipFile(output, "w", compression=zipfile.ZIP_DEFLATED) as archive:
        for name in LEDGER_FILES:
            archive.writestr(name, files[name])
        archive.writestr("manifest.json", _canonical_json(manifest))
    bundle = output.getvalue()
    if len(bundle) > MAX_BUNDLE_BYTES:
        raise MarketLedgerArtifactError("market ledger bundle exceeds the size limit")
    return bundle, manifest


def verify_bundle(bundle: bytes, task_input: dict,
                  diagnostic_context: dict[str, str]) -> tuple[dict, dict[str, bytes]]:
    if not bundle or len(bundle) > MAX_BUNDLE_BYTES:
        raise MarketLedgerArtifactError("market ledger bundle size is invalid")
    try:
        with zipfile.ZipFile(io.BytesIO(bundle)) as archive:
            names = archive.namelist()
            if sorted(names) != sorted((*LEDGER_FILES, "manifest.json")) or len(set(names)) != len(names):
                raise MarketLedgerArtifactError("market ledger bundle allowlist mismatch")
            for info in archive.infolist():
                if info.is_dir() or "/" in info.filename or "\\" in info.filename \
                        or info.file_size <= 0 or info.file_size > MAX_FILE_BYTES:
                    raise MarketLedgerArtifactError("market ledger bundle path or size is invalid")
            manifest = json.loads(archive.read("manifest.json"))
            files = {name: archive.read(name) for name in LEDGER_FILES}
    except (OSError, ValueError, zipfile.BadZipFile, KeyError) as failure:
        raise MarketLedgerArtifactError("market ledger bundle is corrupt") from failure
    declared_hash = manifest.pop("manifestHash", None)
    calculated_hash = _sha256(_canonical_json(manifest))
    manifest["manifestHash"] = declared_hash
    if declared_hash != calculated_hash:
        raise MarketLedgerArtifactError("market ledger manifest checksum mismatch")
    binding = _runtime_binding(task_input, diagnostic_context)
    expected_source = task_input.get("sourceRun")
    artifact = task_input.get("ledgerArtifact") or {}
    source_binding = ({
        "projectId": binding["projectId"],
        "conceptId": binding["conceptId"],
        "conceptSnapshotHash": artifact.get("sourceConceptSnapshotHash"),
        "canonicalInputHash": artifact.get("sourceCanonicalInputHash"),
        "marketTaskRunId": artifact.get("sourceMarketTaskRunId"),
        "taskAttemptId": artifact.get("sourceTaskAttemptId"),
        "asOf": artifact.get("sourceAsOf"),
    } if artifact else binding)
    expected = {**source_binding, "artifactContractVersion": CONTRACT_VERSION,
                "sourceRunId": expected_source}
    if any(value is None or value == "" for value in expected.values()):
        raise MarketLedgerArtifactError("market ledger source binding is incomplete")
    if any(manifest.get(key) != value for key, value in expected.items()) \
            or (artifact and manifest.get("manifestHash") != artifact.get("manifestHash")):
        raise MarketLedgerArtifactError("market ledger lineage mismatch")
    declarations = {item.get("name"): item for item in manifest.get("files", [])
                    if isinstance(item, dict)}
    if set(declarations) != set(LEDGER_FILES):
        raise MarketLedgerArtifactError("market ledger manifest file list mismatch")
    for name, content in files.items():
        declared = declarations[name]
        if declared.get("sizeBytes") != len(content) or declared.get("sha256") != _sha256(content):
            raise MarketLedgerArtifactError("market ledger file checksum mismatch")
    return manifest, files


async def persist(task_input: dict, diagnostic_context: dict[str, str],
                  workspace: str, source_run_id: str) -> dict:
    bundle, manifest = build_bundle(task_input, diagnostic_context, workspace, source_run_id)
    base_url, token = _backend_config()
    url = (f"{base_url}/internal/v1/ai/market-ledger-artifacts/"
           f"{diagnostic_context['taskRunId']}/{diagnostic_context['taskAttemptId']}")
    try:
        async with httpx.AsyncClient(timeout=httpx.Timeout(30.0)) as client:
            response = await client.post(url, content=bundle,
                headers={"X-AI-Internal-Token": token, "Content-Type": CONTENT_TYPE})
            response.raise_for_status()
            uploaded = response.json()
    except (httpx.HTTPError, ValueError) as failure:
        raise MarketLedgerArtifactError("market ledger artifact upload failed") from failure
    if uploaded.get("manifestHash") != manifest["manifestHash"] \
            or not isinstance(uploaded.get("artifactId"), str):
        raise MarketLedgerArtifactError("market ledger upload acknowledgement mismatch")
    return uploaded


async def restore(task_input: dict, diagnostic_context: dict[str, str], workspace: str) -> None:
    artifact = task_input.get("ledgerArtifact")
    if not isinstance(artifact, dict):
        return
    artifact_id = artifact.get("artifactId")
    if not isinstance(artifact_id, str) or not artifact_id:
        raise MarketLedgerArtifactError("market ledger artifact id is missing")
    base_url, token = _backend_config()
    url = (f"{base_url}/internal/v1/ai/market-ledger-artifacts/"
           f"{diagnostic_context['taskRunId']}/{diagnostic_context['taskAttemptId']}/{artifact_id}")
    try:
        async with httpx.AsyncClient(timeout=httpx.Timeout(30.0)) as client:
            response = await client.get(url, headers={"X-AI-Internal-Token": token})
            response.raise_for_status()
            bundle = response.content
    except httpx.HTTPError as failure:
        raise MarketLedgerArtifactError("market ledger artifact restore failed") from failure
    if response.headers.get("X-Artifact-SHA256") != _sha256(bundle):
        raise MarketLedgerArtifactError("market ledger object checksum mismatch")
    restore_bundle(bundle, task_input, diagnostic_context, workspace)


def restore_bundle(bundle: bytes, task_input: dict,
                   diagnostic_context: dict[str, str], workspace: str) -> None:
    """Verify and atomically materialize a durable ledger into a task workspace."""
    manifest, files = verify_bundle(bundle, task_input, diagnostic_context)
    source_run = manifest["sourceRunId"]
    target = Path(workspace, "runs-generated", source_run)
    target.parent.mkdir(parents=True, exist_ok=True)
    temporary = target.parent / f".{source_run}.restore-{uuid.uuid4().hex}"
    temporary.mkdir(exist_ok=False)
    try:
        for name in LEDGER_FILES:
            (temporary / name).write_bytes(files[name])
        temporary.replace(target)
    except Exception:
        shutil.rmtree(temporary, ignore_errors=True)
        raise
