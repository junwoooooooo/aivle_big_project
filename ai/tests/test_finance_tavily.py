import asyncio

from app.tasks.finance_estimate import tavily


def test_tavily_is_disabled_without_key(monkeypatch):
    monkeypatch.delenv("TAVILY_API_KEY", raising=False)
    assert asyncio.run(tavily.search_finance_benchmarks("unitPrice")) == []


def test_tavily_http_failure_is_fail_open(monkeypatch):
    monkeypatch.setenv("TAVILY_API_KEY", "test-key")

    class Client:
        async def __aenter__(self): return self
        async def __aexit__(self, *_args): return None
        async def post(self, *_args, **_kwargs):
            raise tavily.httpx.ConnectError("offline")

    monkeypatch.setattr(tavily.httpx, "AsyncClient", lambda **_kwargs: Client())
    assert asyncio.run(tavily.search_finance_benchmarks("monthlyChurnRate")) == []
