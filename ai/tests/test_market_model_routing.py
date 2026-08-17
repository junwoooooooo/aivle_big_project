import importlib
import sys
from pathlib import Path

AI_ROOT = Path(__file__).resolve().parents[1]
R2 = AI_ROOT / "app" / "research" / "research2"
for path in (R2, R2 / "adapters", R2 / "blocks", R2 / "service"):
    if str(path) not in sys.path:
        sys.path.insert(0, str(path))


def test_market_models_have_independent_defaults_and_do_not_touch_twin(monkeypatch):
    monkeypatch.delenv("MARKET_SEARCH_MODEL", raising=False)
    monkeypatch.delenv("MARKET_EXTRACT_MODEL", raising=False)
    monkeypatch.delenv("MARKET_DESIGN_MODEL", raising=False)
    from app.research.research2.adapters import web
    from app.research.research2.blocks import a_design
    importlib.reload(web)
    importlib.reload(a_design)
    assert web.SEARCH_MODEL == "gpt-5.4-nano"
    assert web.EXTRACT_MODEL == "gpt-5.6-luna"
    assert a_design.MODEL == "gpt-5.6-luna"
    twin = (Path(__file__).parents[1] / "app" / "twin" / "runner.py").read_text(encoding="utf-8")
    assert "MARKET_SEARCH_MODEL" not in twin
    assert "MARKET_EXTRACT_MODEL" not in twin
    assert "MARKET_DESIGN_MODEL" not in twin


def test_market_model_overrides_are_workload_specific(monkeypatch):
    monkeypatch.setenv("MARKET_SEARCH_MODEL", "search-role")
    monkeypatch.setenv("MARKET_EXTRACT_MODEL", "extract-role")
    monkeypatch.setenv("MARKET_DESIGN_MODEL", "design-role")
    from app.research.research2.adapters import web
    from app.research.research2.blocks import a_design
    importlib.reload(web)
    importlib.reload(a_design)
    assert (web.SEARCH_MODEL, web.EXTRACT_MODEL, a_design.MODEL) == (
        "search-role", "extract-role", "design-role")
