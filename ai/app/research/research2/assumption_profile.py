"""Product와 donor fixture의 가정 규칙 authority를 분리한다."""
from __future__ import annotations

import os


PROFILE_ENV = "RESEARCH2_ASSUMPTION_PROFILE"
FIXTURE_PROFILE = "fixture"
PRODUCT_PROFILE = "product"
PRODUCT_RULE_FILE = "assumptions.product.v1.json"


def current_profile() -> str:
    profile = (os.environ.get(PROFILE_ENV) or FIXTURE_PROFILE).strip().lower()
    if profile not in {FIXTURE_PROFILE, PRODUCT_PROFILE}:
        raise ValueError(f"unsupported research assumption profile: {profile}")
    return profile


def rule_file(default_file: str) -> str:
    return PRODUCT_RULE_FILE if current_profile() == PRODUCT_PROFILE else default_file


def is_product_profile() -> bool:
    return current_profile() == PRODUCT_PROFILE
