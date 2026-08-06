"""New-pipeline provider boundary.

The legacy-named module remains the current transport implementation until R7 cleanup;
new tasks depend only on this approved adapter path.
"""

from app.services.journey_provider import ProviderFailure, execute_structured_prompt

__all__ = ["ProviderFailure", "execute_structured_prompt"]
