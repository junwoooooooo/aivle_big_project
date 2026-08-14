"""Small, fail-open Tavily context lookup for financial input recommendations."""

import os
from typing import Any

import httpx


_FIELD_QUERY = {
    "annualFixedLaborCost": "한국 스타트업 연간 인건비 벤치마크",
    "annualFixedRentAndManagementCost": "한국 소상공인 임차료 관리비 벤치마크",
    "annualFixedInfrastructureCost": "한국 SaaS 인프라 운영비 벤치마크",
    "initialDevelopmentAndRnDCost": "한국 서비스 초기 개발 R&D 비용 벤치마크",
    "initialEquipmentAndInfrastructureCost": "한국 사업 초기 설비 인프라 비용 벤치마크",
    "initialPatentAndLicensingCost": "한국 특허 라이선스 초기 비용 벤치마크",
    "totalMarketingCost": "한국 스타트업 마케팅 비용 벤치마크",
    "totalSalesCost": "한국 스타트업 영업 비용 벤치마크",
    "newCustomerCount": "한국 소상공인 신규 고객 확보 벤치마크",
    "monthlyChurnRate": "한국 SaaS 월 이탈률 벤치마크",
    "monthlySubscriptionPrice": "한국 SaaS 월 구독 가격 벤치마크",
    "unitPrice": "한국 제품 서비스 가격 벤치마크",
    "unitVariableCost": "한국 SaaS 구독자당 월 변동비 API 클라우드 고객지원 벤치마크",
    "paymentFee": "한국 온라인 결제 수수료 거래당 비용 벤치마크",
    "partnerPayout": "한국 플랫폼 파트너 수익배분 건당 지급액 벤치마크",
    "shippingCost": "한국 상품 배송비 건당 벤치마크",
    "customerIncrementalInfraCost": "한국 SaaS 고객 증가분 인프라 비용 구독자당 벤치마크",
}


async def search_finance_benchmarks(field_key: str) -> list[dict[str, str]]:
    api_key = os.getenv("TAVILY_API_KEY", "").strip()
    if not api_key:
        return []
    query = _FIELD_QUERY.get(field_key, "한국 사업 재무 비용 벤치마크")
    try:
        async with httpx.AsyncClient(timeout=8.0) as client:
            response = await client.post("https://api.tavily.com/search", json={
                "api_key": api_key, "query": query, "search_depth": "basic", "max_results": 3,
                "include_answer": False, "include_raw_content": False,
            })
        if response.status_code != 200:
            return []
        payload: dict[str, Any] = response.json()
        return [{"title": str(item.get("title", ""))[:200], "url": str(item.get("url", ""))[:500],
                 "content": str(item.get("content", ""))[:1200]}
                for item in payload.get("results", []) if item.get("url")][:3]
    except (httpx.HTTPError, ValueError, TypeError):
        return []
