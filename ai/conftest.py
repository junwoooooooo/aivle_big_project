from __future__ import annotations

import shutil
from pathlib import Path
from uuid import uuid4


def pytest_configure(config) -> None:
    """Use an isolated temp root for each pytest process.

    A fixed basetemp can be left with an ACL owned by another Windows execution
    context.  A unique path avoids deleting or reusing another process's files.
    An explicit command-line --basetemp still takes precedence.
    """
    if config.option.basetemp is None:
        isolated_basetemp = config.rootpath / f".pytest-tmp-{uuid4().hex}"
        config.option.basetemp = str(isolated_basetemp)
        config._isolated_basetemp = isolated_basetemp


def pytest_unconfigure(config) -> None:
    """Remove only the isolated temp root created by this pytest process."""
    isolated_basetemp: Path | None = getattr(config, "_isolated_basetemp", None)
    if isolated_basetemp is None:
        return

    try:
        resolved_path = isolated_basetemp.resolve()
        resolved_root = config.rootpath.resolve()
    except OSError:
        return

    if (
        resolved_path.parent == resolved_root
        and resolved_path.name.startswith(".pytest-tmp-")
    ):
        shutil.rmtree(resolved_path, ignore_errors=True)
