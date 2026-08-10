SEMANTIC_FIELDS = [
    "conceptName", "conceptDefinition", "introduction", "coreValue", "targetUsers",
    "industryCategory", "researchScope",
    "targetRegion", "revenueModel", "price", "channels", "differentiators",
    "preMarketSomShareHypothesis", "preMarketSomHypothesis", "problemScenario", "solutionMechanism",
    "featureSet", "actorRoles", "platformRole",
    "operatingModel", "partnerModel", "providerRole", "sellerRole", "intermediaryRole",
    "transactionFlow", "paymentFlow", "personalDataUsage",
    "physicalActivities", "partnerRequirements", "qualificationRequirements", "advertisingClaims",
]


def valid_candidate(strategy="EXPLORE", index=1):
    semantics = []
    for field in SEMANTIC_FIELDS:
        hypothesis = field.startswith("preMarketSom") or field in {
            "targetRegion", "revenueModel", "price", "channels", "differentiators",
        }
        semantics.append({
            "fieldKey": field,
            "source": "AI_HYPOTHESIS" if hypothesis else "CONCEPT_GENERATED",
            "authority": "OPEN",
            "decision": "PROPOSED",
        })
    if strategy == "AS_IS" and index == 1:
        for item in semantics:
            if item["fieldKey"] in {"conceptDefinition", "problemScenario", "targetUsers"}:
                item.update(source="USER_INPUT", authority="LOCKED", decision="ACCEPTED")
    return {
        "schemaVersion": "2.0", "generationStrategy": strategy, "candidateIndex": index,
        "originalCandidate": strategy == "AS_IS" and index == 1,
        "conceptName": "예약 도우미", "conceptDefinition": "소형 매장의 예약 확인 자동화",
        "introduction": "반복 확인 업무를 줄입니다.", "coreValue": "확인 업무 절감",
        "targetUsers": "예약 서비스를 운영하는 소형 매장", "industryCategory": "예약 관리",
        "researchScope": "국내 소형 예약 사업자", "targetRegion": "대한민국",
        "revenueModel": "사업자 월 구독", "price": "월 9,900원", "channels": "웹과 직접 영업",
        "differentiators": "당일 설치 가능한 간단한 확인 자동화",
        "preMarketSomShareHypothesis": {"targetSharePercent": 2.0, "horizonYears": 3,
            "rationale": "초기 업종 집중 가설", "assumptions": ["유료 전환 가설"]},
        "preMarketSomHypothesis": {"amount": 100000000.0, "currency": "KRW", "period": "연간",
            "calculationBasis": "가설 고객 수와 구독료의 곱", "assumptions": ["고객 수 가설"],
            "confidence": "LOW"},
        "problemScenario": "반복 예약 확인이 필요합니다.",
        "solutionMechanism": "온라인 알림과 예약 관리를 제공합니다.", "featureSet": ["예약 알림"],
        "actorRoles": ["이용자", "예약 사업자"], "platformRole": "예약 정보 전달 중개",
        "operatingModel": "예약 사업자가 직접 등록하고 운영", "partnerModel": "사업자 직접 가입",
        "providerRole": "플랫폼 운영자가 예약 정보 전달 기능을 제공",
        "sellerRole": "예약 사업자가 서비스 판매자 역할을 담당",
        "intermediaryRole": "플랫폼은 예약 정보를 전달하는 중개자 역할을 담당",
        "transactionFlow": ["이용자가 예약", "사업자가 확정"], "paymentFlow": ["사업자가 구독료 결제"],
        "personalDataUsage": ["예약 연락처 처리"], "physicalActivities": [],
        "partnerRequirements": [], "qualificationRequirements": [],
        "advertisingClaims": ["예약 확인 자동화"], "constraintCompliance": [],
        "valueSemantics": semantics,
    }


def valid_legal_fact_pattern(value=None):
    candidate = value or valid_candidate()
    semantics = {item["fieldKey"]: item for item in candidate["valueSemantics"]}

    def governed(field):
        return {"value": candidate[field], **{
            key: semantics[field][key] for key in ("source", "authority", "decision")
        }}

    def sensitive(field, sensitivity):
        return {**governed(field), "legalSensitivity": sensitivity}

    return {
        "schemaVersion": "2.0", "jurisdiction": "KR",
        "actorRoles": governed("actorRoles"),
        "platformRole": governed("platformRole"),
        "commercialRoles": {
            "providerRole": governed("providerRole"),
            "sellerRole": governed("sellerRole"),
            "intermediaryRole": governed("intermediaryRole"),
        },
        "transactionFlow": governed("transactionFlow"),
        "paymentFlow": governed("paymentFlow"),
        "personalDataUsage": governed("personalDataUsage"),
        "physicalActivities": governed("physicalActivities"),
        "partnerRoles": {"partnerModel": governed("partnerModel"),
            "partnerRequirements": governed("partnerRequirements")},
        "qualificationRequirements": governed("qualificationRequirements"),
        "advertisingClaims": governed("advertisingClaims"),
        "operatingModel": governed("operatingModel"),
        "hypotheses": {
            "targetRegion": sensitive("targetRegion", "LEGAL_SENSITIVE"),
            "revenueModel": sensitive("revenueModel", "LEGAL_SENSITIVE"),
            "price": sensitive("price", "LEGAL_SENSITIVE"),
            "channels": sensitive("channels", "POTENTIALLY_LEGAL_SENSITIVE"),
            "differentiators": sensitive("differentiators", "POTENTIALLY_LEGAL_SENSITIVE"),
        },
    }
