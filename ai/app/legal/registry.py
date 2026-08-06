import json
import os
from pathlib import Path
from typing import Any


ASSET_ROOT = Path(__file__).resolve().parent / "assets"


class RegistryError(ValueError):
    pass


def _read(name: str) -> dict[str, Any]:
    try:
        value = json.loads((ASSET_ROOT / name).read_text(encoding="utf-8"))
    except (OSError, json.JSONDecodeError) as failure:
        raise RegistryError(f"LEGAL_REGISTRY_INVALID:{name}") from failure
    if not isinstance(value, dict):
        raise RegistryError(f"LEGAL_REGISTRY_INVALID:{name}")
    return value


class LegalRegistry:
    def __init__(self):
        self.registry = _read("law_registry.json")
        self.category_map = _read("category_map.json")
        self.category_rules = _read("category_rules.json")
        self.version = self.registry.get("_meta", {}).get("version")
        configured = os.getenv("LEGAL_REGISTRY_VERSION", "legal-registry-v1").strip()
        if not self.version or configured != self.version:
            raise RegistryError("LEGAL_REGISTRY_VERSION_MISMATCH")
        routes = self.registry.get("routes")
        route_categories = self.category_map.get("routes")
        categories = self.category_map.get("categories")
        if not isinstance(routes, dict) or not isinstance(route_categories, dict) or not isinstance(categories, dict):
            raise RegistryError("LEGAL_REGISTRY_INVALID")
        for route_id, route in routes.items():
            if route_id not in route_categories or not isinstance(route.get("laws"), list):
                raise RegistryError(f"LEGAL_REGISTRY_ROUTE_INVALID:{route_id}")
            for category in route_categories[route_id]:
                if category not in categories:
                    raise RegistryError(f"LEGAL_CATEGORY_UNKNOWN:{category}")

    @property
    def routes(self) -> dict[str, Any]:
        return self.registry["routes"]

    @property
    def category_labels(self) -> dict[str, str]:
        return self.category_map["categories"]

    def categories_for_route(self, route_id: str) -> list[str]:
        return list(self.category_map["routes"].get(route_id) or ["INDUSTRY_SPECIFIC"])

    def categories_for_article(self, route_id: str, law_name: str, title: str) -> tuple[list[str], bool]:
        law_rule = self.category_rules.get("laws", {}).get(law_name)
        if not law_rule:
            return self.categories_for_route(route_id), True
        for rule in law_rule.get("rules") or []:
            if any(keyword in (title or "") for keyword in rule.get("titleKeywords") or []):
                return list(rule.get("categories") or law_rule.get("default") or []), False
        return list(law_rule.get("default") or self.categories_for_route(route_id)), False

    def route_catalog_for_prompt(self) -> list[dict[str, Any]]:
        return [{"routeId": route_id, "topic": value["topic"]} for route_id, value in self.routes.items()]
