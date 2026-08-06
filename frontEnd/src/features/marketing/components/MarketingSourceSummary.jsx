import { useState } from 'react';

import { Alert, Button, Card } from '../../../shared/ui/index.js';

function parseSnapshot(value) {
  try {
    return typeof value === 'string' ? JSON.parse(value) : value ?? {};
  } catch {
    return {};
  }
}

function SourceSection({ title, status, children }) {
  const [open, setOpen] = useState(false);
  return (
    <section className="marketing-source__section">
      <button type="button" aria-expanded={open} onClick={() => setOpen((value) => !value)}>
        <span>{title}</span><strong>{status}</strong>
      </button>
      {open && <div>{children}</div>}
    </section>
  );
}

export default function MarketingSourceSummary({
  sourceSnapshotJson,
  legalNotice,
  copyEvidence = [],
  onRefresh,
  refreshDisabled,
}) {
  const snapshot = parseSnapshot(sourceSnapshotJson);
  const persona = snapshot.persona;
  const panel = snapshot.panelInterview ?? {};
  const market = snapshot.marketResponse ?? {};
  return (
    <Card className="marketing-source">
      <div className="marketing-panel__heading">
        <div><h2>검증 결과</h2><p>Snapshot v{snapshot.sourceSnapshotVersion ?? 1}</p></div>
        {onRefresh && <Button size="small" variant="outline" disabled={refreshDisabled} onClick={onRefresh}>다시 불러오기</Button>}
      </div>
      <SourceSection title="프로젝트" status="반영됨">
        <p>{snapshot.project?.title || '현재 프로젝트'}</p>
      </SourceSection>
      <SourceSection title="Persona" status={persona?.available ? '반영됨' : '결과 없음'}>
        <p>{persona?.name || '직접 입력 중심'}</p>
      </SourceSection>
      <SourceSection
        title="사업성·법률"
        status={snapshot.legalReview?.available || snapshot.feasibility?.available ? '반영됨' : '결과 없음'}
      >
        <p>{snapshot.feasibility?.summary || '사업성 결과 없음'}</p>
        <p>{snapshot.legalReview?.summary || '법률 결과 없음'}</p>
      </SourceSection>
      <SourceSection title="패널 인터뷰" status={panel.status === 'INCLUDED' ? '반영됨' : '미반영'}>
        <p>{panel.title || '선택한 패널 인터뷰 없음'}</p>
      </SourceSection>
      <SourceSection title="시장 반응" status={market.status === 'INCLUDED' ? '반영됨' : '미반영'}>
        <p>{market.title || '선택한 시장 반응 결과 없음'}</p>
        {market.bestMessageText && <p>반응 메시지: {market.bestMessageText}</p>}
      </SourceSection>
      <SourceSection title="사용자 입력" status="반영됨">
        <p>{snapshot.userInput?.targetOffer || '직접 입력한 메시지 없음'}</p>
      </SourceSection>
      {!persona?.available && (
        <Alert title="반영 가능한 검증 결과가 제한적입니다">
          검증 결과 없이도 직접 입력해 콘텐츠를 계속 제작할 수 있습니다.
        </Alert>
      )}
      {copyEvidence.length > 0 && (
        <section className="marketing-copy-evidence">
          <h3>카피 반영 근거</h3>
          <ul>{copyEvidence.map((item) => <li key={item}>{item}</li>)}</ul>
        </section>
      )}
      <Alert title="광고 표현 주의사항" tone={snapshot.legalReview?.available ? 'warning' : 'info'}>
        {legalNotice}
      </Alert>
    </Card>
  );
}
