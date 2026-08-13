const priorityLabel = { CRITICAL: '즉시 확인', HIGH: '우선 확인', MEDIUM: '확인 필요', LOW: '참고' };
const decisionLabel = { GO: '진행 가능', REVISE: '보완 후 진행', NO_GO: '진행 보류' };

function shortText(value, limit = 150) {
  const text = String(value ?? '')
    .replace(/\s*.?FACT-\d+(?:\s*[,·/]\s*FACT-\d+)*.?/gi, '')
    .replace(/\s*(?:근거\s*(?:ID|번호)\s*[:：]?\s*)?(?:FACT-\d+(?:\s*[,·/]\s*FACT-\d+)*)/gi, '')
    .replace(/\s{2,}/g, ' ')
    .trim();
  return text.length > limit ? `${text.slice(0, limit).trim()}…` : text;
}

export default function CommercializationAdvisory({ report, onDownload, downloading }) {
  if (!report?.result) return null;
  const result = report.result;
  const priorities = [...(result.advice ?? []), ...(result.readiness ?? [])]
    .filter((item) => ['CRITICAL', 'HIGH'].includes(item.priority)).slice(0, 3);
  const pilot = result.pilotPlan;
  return <section className="commercialization-summary" aria-live="polite">
    <header><div><p>기술·운영 분석 요약</p><h2>{result.productName || '기술·운영 적용성 검증'}</h2><strong>{shortText(result.summary, 240)}</strong></div><span className={`commercialization-summary__decision ${String(result.decision ?? '').toLowerCase()}`}>{decisionLabel[result.decision] ?? result.decision ?? '판정 대기'}</span></header>
    <div className="commercialization-summary__grid"><article><span>핵심 권고</span><strong>{result.advice?.length ?? 0}건</strong><small>전체 권고안은 다운로드 문서에서 확인할 수 있습니다.</small></article><article><span>출시 게이트</span><strong>{result.gates?.length ?? 0}개</strong><small>통과 기준과 담당자를 문서에 함께 정리합니다.</small></article><article><span>외부·시장 근거</span><strong>{result.layer2Evidence?.length ?? 0}건</strong><small>시장·BM 확인 사실과 외부 출처를 포함합니다.</small></article></div>
    {priorities.length > 0 && <section className="commercialization-summary__priorities"><h3>먼저 확인할 사항</h3><ul>{priorities.map((item, index) => <li key={`${item.area ?? item.topic}-${index}`}><b>{priorityLabel[item.priority] ?? item.priority}</b> {shortText(item.advice ?? item.assessment)}</li>)}</ul></section>}
    {pilot && <section className="commercialization-summary__pilot"><h3>파일럿 핵심</h3><p><b>목표:</b> {shortText(pilot.objective)}</p><p><b>측정 지표:</b> {(pilot.metrics ?? []).slice(0, 3).join(' · ') || '문서에서 확인'}</p></section>}
    <button type="button" className="commercialization-summary__download" onClick={onDownload} disabled={downloading}>{downloading ? '보고서 생성 중…' : '기술·운영 분석 전체 보고서 다운로드 (.docx)'}</button>
  </section>;
}
