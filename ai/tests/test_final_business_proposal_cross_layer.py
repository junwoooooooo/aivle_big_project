import json
import os
from pathlib import Path

import pytest

from app.tasks.final_business_proposal.models import FinalBusinessProposalInput


FIXTURE = os.getenv("FINAL_PROPOSAL_FIXTURE_INPUT")


@pytest.mark.skipif(not FIXTURE, reason="backend contract fixture path is not configured")
def test_backend_produced_input_passes_the_python_contract():
    raw = json.loads(Path(FIXTURE).read_text(encoding="utf-8"))
    value = FinalBusinessProposalInput.model_validate(raw)

    assert len(value.sourceManifest) >= 3
    assert len(value.includedSourceTypes) >= 3
    assert value.sourceManifestHash.startswith("sha256:")
    assert len(value.evidenceCatalog) >= 1
    assert len(value.allowedEvidenceKeys) >= 1
    assert set(value.allowedEvidenceKeys) == {
        item.evidenceKey for item in value.evidenceCatalog
    }
