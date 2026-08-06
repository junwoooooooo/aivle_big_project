import re
from pathlib import Path

from app.api.executions import TASK_TYPES


EXPECTED_TASK_TYPES = {
    "IDEA_INTERPRETATION",
    "IDEA_CONVERSATION_TURN",
    "REGULATORY_BOUNDARY_GENERATION",
    "LEGAL_REVIEW",
    "IDEA_LEGAL_PRECHECK",
    "CONCEPT_LEGAL_VALIDATION",
    "CONCEPT_GENERATION",
    "CONCEPT_EXPLORATION",
    "QUICK_ASSESSMENT",
    "DETAILED_ANALYSIS",
    "PERSONA_CARD_GENERATION",
    "PERSONA_INTERVIEW",
    "INTERVIEW_SYNTHESIS",
    "MARKETING_GENERATION",
    "MARKETING_COMPARISON",
    "FINAL_REPORT_GENERATION",
}


NON_AI_TASK_TYPES = {"IDEA_ATTACHMENT_PARSE"}


def test_java_and_fastapi_task_types_are_aligned_with_internal_worker_types():
    java_enum = (
        Path(__file__).resolve().parents[2]
        / "backend/src/main/java/com/aivle/backend/taskrun/domain/TaskType.java"
    ).read_text(encoding="utf-8")
    enum_body = re.search(r"enum\s+TaskType\s*\{([^}]*)\}", java_enum, re.DOTALL)
    assert enum_body is not None
    java_task_types = {
        value.strip()
        for value in enum_body.group(1).split(",")
        if value.strip()
    }

    assert TASK_TYPES == EXPECTED_TASK_TYPES
    assert java_task_types == EXPECTED_TASK_TYPES | NON_AI_TASK_TYPES
    assert len(java_task_types) == 17
