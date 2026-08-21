"""Canonical value shapes for the seven Concept Portfolio hypotheses."""

from __future__ import annotations

import re
from typing import Any

from pydantic import ValidationError

from app.tasks.concept_candidate.models import (
    PreMarketSomHypothesis,
    PreMarketSomShareHypothesis,
)


TEXT_HYPOTHESES = frozenset({
    "TARGET_REGION", "REVENUE_MODEL", "PRICE", "CHANNELS", "DIFFERENTIATORS",
})
LIST_COMPATIBLE_TEXT_HYPOTHESES = frozenset({"CHANNELS", "DIFFERENTIATORS"})
_ONE_LINE = re.compile(r"\s+")


class HypothesisValueContractError(ValueError):
    """A hypothesis value cannot be represented by the canonical Candidate contract."""


def normalize_hypothesis_value(hypothesis_type: str, value: Any) -> Any:
    """Return a canonical JSON value without stringifying arbitrary data.

    Existing strings are returned byte-for-byte. Only legacy/provider list values for
    CHANNELS and DIFFERENTIATORS have a lossless compatibility normalization.
    """
    if hypothesis_type in TEXT_HYPOTHESES:
        if isinstance(value, str):
            if not value.strip():
                raise HypothesisValueContractError(f"{hypothesis_type} must be a non-empty string")
            return value
        if hypothesis_type in LIST_COMPATIBLE_TEXT_HYPOTHESES and isinstance(value, list):
            if not value:
                raise HypothesisValueContractError(f"{hypothesis_type} list must not be empty")
            items: list[str] = []
            for item in value:
                if not isinstance(item, str) or not item.strip():
                    raise HypothesisValueContractError(
                        f"{hypothesis_type} list items must be non-empty strings"
                    )
                items.append(_ONE_LINE.sub(" ", item.strip()))
            return ", ".join(items)
        raise HypothesisValueContractError(f"{hypothesis_type} must be a string")

    model = {
        "PRE_MARKET_SOM_SHARE": PreMarketSomShareHypothesis,
        "PRE_MARKET_SOM": PreMarketSomHypothesis,
    }.get(hypothesis_type)
    if model is None:
        raise HypothesisValueContractError(f"unsupported hypothesis type: {hypothesis_type}")
    try:
        return model.model_validate(value).model_dump(mode="json")
    except ValidationError as failure:
        raise HypothesisValueContractError(
            f"{hypothesis_type} must match its structured object contract"
        ) from failure
