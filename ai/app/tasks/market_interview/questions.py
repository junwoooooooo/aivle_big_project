QUESTIONS = (
    ("firstImpression", "첫인상", "방금 보신 것에 대해 떠오르는 대로 말씀해주세요."),
    ("restatement", "이해", "이 제품이 뭘 해주는 건지 본인 말로 설명해주시겠어요?"),
    ("like", "끌리는 점", "가장 마음에 드는 점과 그 이유는 무엇인가요?"),
    ("concern", "걸리는 점", "마음에 안 들거나 걸리는 점과 비교 대상은 무엇인가요?"),
    ("differentiation", "차별성", "지금 있는 것들과 무엇이 다르다고 느끼나요?"),
    ("relevance", "필요성", "본인 상황에 필요한가요? 지금은 어떻게 해결하나요?"),
    ("usageScene", "사용 장면", "언제, 어떤 상황에서 사용할 것 같나요?"),
    ("barrier", "안 사는 이유", "안 산다면 가장 큰 이유와 그 이유가 사라질 때의 생각은 무엇인가요?"),
    ("suggestion", "바꾸고 싶은 것", "하나만 바꿀 수 있다면 무엇을 바꾸겠나요?"),
)


def concept_board(value) -> str:
    concept = value.selectedConcept
    identity = concept.get("identity") or {}
    solution = concept.get("solution") or {}
    lines = [f"이름: {identity.get('name') or '이름 미정'}"]
    target = identity.get("targetUsers") or concept.get("targetUsers")
    if target: lines.append(f"누구를 위한 것인가: {target}")
    problem = concept.get("problem") or concept.get("problemScenario")
    if problem: lines.append(f"어떤 상황의 문제인가: {problem}")
    features = solution.get("featureSet") or concept.get("featureSet") or []
    if features: lines.append("하는 일: " + " · ".join(str(item) for item in features[:12]))
    return "\n".join(lines)


def build_prompt(card_text: str, board: str) -> str:
    questions = "\n".join(f"{i}. {title} — {text}" for i, (_, title, text) in enumerate(QUESTIONS, 1))
    return f"""{card_text}\n\n당신은 위 프로필을 바탕으로 만든 AI 가상 응답자입니다.
모든 응답자에게 동일한 아래 상품 설명과 질문을 제시합니다. 실제 인물의 발언이라고 주장하지 마세요.
--- 상품 설명 ---\n{board}\n-----------------\n{questions}
이 프로필의 형편에서 답마다 1~3문장으로 답하세요. 설명에 없는 기능이나 경험을 사실처럼 만들지 마세요."""
