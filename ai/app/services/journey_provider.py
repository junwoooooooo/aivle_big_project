"""Compatibility import for shared provider transport.

Legacy journey task dispatch was removed in R7A. New code imports ``app.providers``.
"""

from app.providers.structured import ProviderFailure, execute_structured_prompt

__all__ = ["ProviderFailure", "execute_structured_prompt"]
