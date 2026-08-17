import copy
import importlib
import importlib.util
import json
import os
import sys
from pathlib import Path
from types import SimpleNamespace

import pytest

from app.providers import ProviderFailure
from app.research import pipeline, product_pipeline


class _FakeBudget:
    deadline_monotonic = None

    def __init__(self):
        self.charged = 0

    def can_afford(self, _value):
        return True

    def charge(self, value):
        self.charged += value

    def remaining(self):
        return 100


def _modules(monkeypatch, design, seen):
    class HarnessOptions:
        def __init__(self, **values):
            self.__dict__.update(values)

    class DryrunOptions:
        def __init__(self, **values):
            self.__dict__.update(values)

    class CollectOptions:
        def __init__(self, **values):
            self.__dict__.update(values)

    harness = SimpleNamespace(HarnessOptions=HarnessOptions, run_harness=lambda options: design)
    def run_dryrun(options):
        seen['dryrun'] = options
        return {}

    dryrun = SimpleNamespace(DryrunOptions=DryrunOptions, dryrun=run_dryrun)

    def collect(options):
        seen['collect'] = options
        return {'metrics': {'llm.calls': 1}}

    engine = SimpleNamespace(CollectOptions=CollectOptions, collect=collect)
    monkeypatch.setitem(sys.modules, 'slot_harness', harness)
    monkeypatch.setitem(sys.modules, 'slot_dryrun', dryrun)
    monkeypatch.setitem(sys.modules, 'run', engine)


def test_same_harness_snapshot_is_consumed_by_same_market_run(tmp_path, monkeypatch):
    slots = tmp_path / 'snapshots' / 'slots_run-41.json'
    formulas = tmp_path / 'snapshots' / 'formulas_run-41.json'
    slots.parent.mkdir()
    slots.write_text(json.dumps({'slots': []}), encoding='utf-8')
    formulas.write_text(json.dumps({'formulas': []}), encoding='utf-8')
    seen = {}
    _modules(monkeypatch, {'passed': True, 'report': {'시도_기록': [{}]},
        'snapshot': {'slots': str(slots), 'formulas': str(formulas)}}, seen)

    pipeline._collect(pipeline.Run(), _FakeBudget(), 'concept.json', 'run-41', '2026-08-17')

    assert seen['dryrun'].tag == 'run-41'
    assert seen['dryrun'].slots == str(slots)
    assert seen['collect'].id == 'run-41'
    assert seen['collect'].slots == str(slots)
    assert seen['collect'].formulas == str(formulas)


def test_real_research2_harness_writes_and_dryrun_reads_same_dynamic_snapshot(
        tmp_path, monkeypatch, capsys):
    """실제 Research2 producer/무료 consumer를 사용하고 유료 LLM만 replay로 대체한다."""
    root = Path(pipeline.RESEARCH_HOME)
    for directory in (root / 'harness', root / 'tools', root / 'adapters', root):
        if str(directory) not in sys.path:
            sys.path.insert(0, str(directory))

    # Research2가 자체 검증하는 통과 설계를 그대로 replay 입력으로 사용한다. 해당 모듈은
    # 독립 실행형이라 마지막에 SystemExit(0)을 내므로, 검증 완료 뒤의 GOOD만 꺼낸다.
    source = root / 'tests' / 'test_harness.py'
    spec = importlib.util.spec_from_file_location('_research2_verified_harness_design', source)
    verified = importlib.util.module_from_spec(spec)
    sys.modules[spec.name] = verified
    try:
        with pytest.raises(SystemExit) as stopped:
            spec.loader.exec_module(verified)
        assert stopped.value.code in (0, 1)
    finally:
        sys.modules.pop(spec.name, None)
    capsys.readouterr()

    direct = copy.deepcopy(verified.GOOD)
    tam = next(item for item in direct['formulas'] if item['formula_id'] == 'F_TAM')
    tam['vars'] = [tam['vars'][0]]
    sam = copy.deepcopy(tam)
    sam['formula_id'] = 'F_SAM'
    sam['vars'][0]['claim_type'] = 'SAM'
    sam['vars'][0]['region'] = '서울'
    direct['formulas'].insert(direct['formulas'].index(tam) + 1, sam)

    replay = tmp_path / 'dynamic-concept-replay.json'
    replay.write_text(json.dumps({'model': 'provider-boundary-replay', 'usage': {},
        'text': '', 'data': direct, 'repair': ''}, ensure_ascii=False), encoding='utf-8')

    harness = importlib.import_module('slot_harness')
    snapshot_dir = tmp_path / 'snapshots'
    harness_dir = tmp_path / 'harness-output'
    monkeypatch.setenv('RESEARCH2_SNAPSHOT_DIR', str(snapshot_dir))
    monkeypatch.setattr(harness.runpath, 'harness_write_dir', lambda _tag: str(harness_dir))
    design = harness.run_harness(harness.HarnessOptions(
        concept=str(root / 'data' / 'concept_beauty-noshow.json'),
        tag='task-run-dynamic-41', replay=str(replay), as_of=2026))

    assert design['passed'] is True
    assert design['snapshot']['slots'] == os.path.abspath(
        snapshot_dir / 'slots_task-run-dynamic-41.json')
    assert design['snapshot']['formulas'] == os.path.abspath(
        snapshot_dir / 'formulas_task-run-dynamic-41.json')
    assert Path(design['snapshot']['slots']).is_file()
    assert Path(design['snapshot']['formulas']).is_file()

    dryrun = importlib.import_module('slot_dryrun')
    dryrun_dir = tmp_path / 'dryrun-output'
    monkeypatch.setattr(dryrun.runpath, 'write_dir', lambda _tag: str(dryrun_dir))
    report = dryrun.dryrun(dryrun.DryrunOptions(
        tag='task-run-dynamic-41', slots=design['snapshot']['slots'], no_net=True))
    persisted = json.loads(Path(design['snapshot']['slots']).read_text(encoding='utf-8'))
    assert report['출처']['경로'] == design['snapshot']['slots']
    assert report['슬롯수'] == len(persisted['slots']) > 0
    assert {row['slot_id'] for row in report['슬롯']} == {
        slot['slot_id'] for slot in persisted['slots']}


@pytest.mark.parametrize('series', ['A', 'B', 'C'])
def test_market_series_use_direct_observation_without_assumption_templates(series):
    root = Path(pipeline.RESEARCH_HOME)
    for directory in (root / 'harness', root):
        if str(directory) not in sys.path:
            sys.path.insert(0, str(directory))
    harness = importlib.import_module('slot_harness')
    vocab = json.loads((root / 'harness' / 'vocab.json').read_text(encoding='utf-8'))
    formulas = {formula_id: template for formula_id, _target, _path, template, _why
                in harness.targets(vocab, {'_계열': {'계열': series}})}
    assert formulas['F_TAM'] == 'T5'
    assert formulas['F_SAM'] == 'T5'


@pytest.mark.parametrize(('design', 'reason'), [
    ({'passed': False, 'report': {'checks': [{'name': 'coverage', 'passed': False}]}},
     'HARNESS_PRECONDITION_FAILED'),
    ({'passed': True, 'report': {}, 'snapshot': {'slots': 'missing-slots.json', 'formulas': 'missing-formulas.json'}},
     'RESEARCH_SNAPSHOT_MISSING'),
])
def test_pipeline_contract_failures_are_non_retryable(design, reason, monkeypatch):
    _modules(monkeypatch, design, {})
    with pytest.raises(pipeline._PipelineContract) as raised:
        pipeline._collect(pipeline.Run(), _FakeBudget(), 'concept.json', 'run-42', '2026-08-17')
    assert raised.value.reason == reason


def test_child_transport_preserves_snapshot_contract_reason(tmp_path):
    path = tmp_path / 'error.json'
    path.write_text(json.dumps({'kind': 'ProviderFailure', 'code': 'EXECUTION_FAILED',
        'reason': 'RESEARCH_SNAPSHOT_MISSING', 'statusCode': 500, 'retryable': False,
        'safeDiagnostics': {'component': 'market-research', 'detail': 'run=42 slots missing'}}), encoding='utf-8')
    failure = product_pipeline._child_failure(str(path))
    assert isinstance(failure, ProviderFailure)
    assert failure.reason == 'RESEARCH_SNAPSHOT_MISSING'
    assert failure.retryable is False
    assert failure.safe_diagnostics['detail'] == 'run=42 slots missing'


def test_zero_resolved_kosis_uses_existing_web_routes_instead_of_hard_failing():
    statuses = pipeline._dryrun_route_statuses({"슬롯": [
        {"route": "kosis", "route_why": "route_metric=사업체 수 (검색 대신 통계 API)",
         "stat_code_대조": ""},
        {"route": "web", "route_why": "기본 경로"},
    ]})

    assert statuses == ["KOSIS_UNRESOLVED_WEB_FALLBACK", "WEB_DIRECT"]


def test_explicit_kosis_without_fallback_is_classified_as_blocked():
    assert pipeline._dryrun_route_statuses({"슬롯": [{
        "route": "kosis", "route_why": "stat_code=101/UNKNOWN", "stat_code_대조": "",
    }]}) == ["BLOCKED_NO_ROUTE"]


def test_market_route_unresolved_taxonomy_is_non_retryable():
    failure = pipeline._fail(
        "EXECUTION_FAILED", "MARKET_ROUTE_UNRESOLVED", "관측 경로 없음")

    assert failure.reason == "MARKET_ROUTE_UNRESOLVED"
    assert failure.retryable is False
