import { Link } from 'react-router-dom';

import {
  Alert,
  Badge,
  Button,
  Card,
  ErrorState,
  LoadingState,
  PageHeader,
  StatusBadge,
} from '../../shared/ui/index.js';
import { useProjectContext } from '../projects/ProjectContext.jsx';
import { downloadReportMarkdown } from './export/reportMarkdownExporter.js';
import { openReportPrintWindow } from './export/reportPrintWindow.js';
import { useIntegratedReport } from './hooks/useIntegratedReport.js';
import ReportStatusCard from './components/ReportStatusCard.jsx';
import ReportPrintDocument from './components/ReportPrintDocument.jsx';
import './report.css';

function ReportSection({ id, section, children }) {
  return (
    <section className="report-section" id={id} aria-labelledby={`${id}-title`}>
      <div className="report-section__heading">
        <div>
          <p>{section.statusView.label}</p>
          <h2 id={`${id}-title`}>{section.title}</h2>
        </div>
        <Badge tone={section.statusView.tone}>{section.statusView.label}</Badge>
      </div>
      {section.data ? children : (
        <Card className="report-section__empty">
          <p>{section.summary}</p>
          {section.error && (
            <Alert title="영역 조회 실패" tone="danger">
              다른 결과는 그대로 표시합니다. 상세 화면에서 이 영역을 다시 확인해 주세요.
            </Alert>
          )}
          <Link className="primary-link" to={section.route}>해당 단계로 이동</Link>
        </Card>
      )}
    </section>
  );
}

function StructuredPlanSection({ section }) {
  if (!section.data) return <ReportSection id="report-structure" section={section} />;
  return (
    <ReportSection id="report-structure" section={section}>
      <Card className="report-summary-card">
        <p>{section.summary}</p>
        <dl className="report-facts">
          <div><dt>계획 상태</dt><dd><StatusBadge status={section.data.status} /></dd></div>
          <div><dt>출처 문서</dt><dd>버전 #{section.data.sourceDocumentVersionId}</dd></div>
          <div><dt>계획 버전</dt><dd>#{section.data.versionNumber}</dd></div>
          <div><dt>확정 시각</dt><dd>{section.confirmedAtLabel}</dd></div>
        </dl>
      </Card>
      <div className="report-section-list">
        {section.sections.map((item) => (
          <Card key={item.sectionCode}>
            <div className="report-item-heading">
              <h3>{item.displayName}</h3>
              <StatusBadge status={item.status} />
            </div>
            <p className="report-clamped-copy">{item.extractedContent || '추출 내용 없음'}</p>
            {item.evidence.length > 0 && <small>연결 근거 {item.evidence.length}건</small>}
          </Card>
        ))}
      </div>
      {(section.filledFields.length > 0 || section.waivedFields.length > 0) && (
        <Card>
          <h3>사용자 보완 내역</h3>
          <ul>
            {section.filledFields.map((field) => (
              <li key={field.fieldId}><strong>{field.label}</strong>: {field.userValue}</li>
            ))}
            {section.waivedFields.map((field) => (
              <li key={field.fieldId}><strong>{field.label}</strong>: 이번 단계에서 제외 — {field.reason}</li>
            ))}
          </ul>
        </Card>
      )}
      <Link to={section.route}>구조화 결과 자세히 보기</Link>
    </ReportSection>
  );
}

function LegalSection({ section }) {
  if (!section.data) return <ReportSection id="report-legal" section={section} />;
  return (
    <ReportSection id="report-legal" section={section}>
      <Card className="report-summary-card">
        <div className="report-item-heading">
          <h3>{section.riskLabel} 위험</h3>
          <StatusBadge status={section.data.status} />
        </div>
        <p>{section.summary}</p>
      </Card>
      {section.importantFindings.length > 0 ? (
        <div className="report-section-list">
          {section.importantFindings.map((item) => (
            <Card key={item.id}>
              <div className="report-item-heading">
                <h3>{item.categoryLabel}</h3>
                <span>{item.riskLevel}</span>
              </div>
              <p>{item.finding}</p>
              <h4>권장 행동</h4>
              <p>{item.recommendedAction}</p>
              {item.requiresProfessionalReview && <strong>전문가 확인 필요</strong>}
            </Card>
          ))}
        </div>
      ) : <p>현재 요약 대상인 고위험 또는 전문가 확인 항목은 없습니다.</p>}
      {section.questions.length > 0 && (
        <Card><h3>중요 확인 질문</h3><ol>{section.questions.slice(0, 5).map((item) => <li key={item.id}>{item.question}</li>)}</ol></Card>
      )}
      <Alert title="법률 사전검토 한계" tone="warning" live={false}>{section.data.disclaimer}</Alert>
      <Link to={section.route}>법률 결과 자세히 보기</Link>
    </ReportSection>
  );
}

function FeasibilitySection({ section }) {
  if (!section.data) return <ReportSection id="report-feasibility" section={section} />;
  return (
    <ReportSection id="report-feasibility" section={section}>
      <Card className="report-summary-card">
        <div className="report-item-heading">
          <h3>{section.verdictLabel}</h3>
          <StatusBadge status={section.data.status} />
        </div>
        <p>{section.summary}</p>
        <dl className="report-facts">
          {section.data.overallScore != null && <div><dt>서버 종합 점수</dt><dd>{section.data.overallScore}</dd></div>}
          <div><dt>신뢰도</dt><dd>{section.data.confidence}</dd></div>
        </dl>
      </Card>
      <div className="report-two-columns">
        <Card><h3>핵심 강점</h3><ul>{section.strengths.map((item) => <li key={item}>{item}</li>)}</ul></Card>
        <Card><h3>중요 위험</h3><ul>{section.risks.map((item) => <li key={item}>{item}</li>)}</ul></Card>
      </div>
      <div className="report-section-list">
        {section.dimensions.map((item) => (
          <details className="report-detail-card" key={item.id}>
            <summary>{item.label} · {item.status}</summary>
            <p>{item.finding}</p>
            {item.assumptions.length > 0 && <><h4>주요 가정</h4><ul>{item.assumptions.map((value) => <li key={value}>{value}</li>)}</ul></>}
            {item.evidence.length > 0 && <><h4>근거 유형</h4><ul>{item.evidence.map((value, index) => <li key={`${value.type}-${index}`}>{value.typeLabel}: {value.description ?? value.content ?? '근거 내용 확인'}</li>)}</ul></>}
          </details>
        ))}
      </div>
      <Alert title="사업 타당성 분석 한계" tone="warning" live={false}>{section.data.disclaimer}</Alert>
      <Link to={section.route}>사업 타당성 결과 자세히 보기</Link>
    </ReportSection>
  );
}

function PersonaSection({ section }) {
  if (!section.data) return <ReportSection id="report-persona" section={section} />;
  return (
    <ReportSection id="report-persona" section={section}>
      <Card className="report-summary-card">
        <div className="report-item-heading">
          <h3>{section.summary}</h3>
          <StatusBadge status={section.data.status} />
        </div>
        <dl className="report-facts">
          <div><dt>카탈로그</dt><dd>{section.data.catalogVersion}</dd></div>
          <div><dt>신뢰도</dt><dd>{section.data.confidence}</dd></div>
        </dl>
      </Card>
      <div className="report-two-columns">
        {section.items.slice(0, 2).map((item) => (
          <Card key={item.id}>
            <div className="report-item-heading">
              <h3>{item.baselinePersona?.displayName ?? item.baselinePersona?.personaCode}</h3>
              <span>적합도 {item.fitScore ?? '정보 부족'}</span>
            </div>
            <p>{item.interpretation}</p>
            <h4>맞는 근거</h4><ul>{item.matchReasons.map((value) => <li key={value}>{value}</li>)}</ul>
            <h4>불일치 위험</h4><ul>{item.mismatchRisks.map((value) => <li key={value}>{value}</li>)}</ul>
          </Card>
        ))}
      </div>
      {section.hypotheses.length > 0 && (
        <Card><h3>우선 검증 고객 가설</h3><ul>{section.hypotheses.slice(0, 5).map((item) => <li key={item.id}><strong>{item.statement}</strong> — {item.rationale}</li>)}</ul></Card>
      )}
      <Alert title="페르소나 해석 한계" tone="warning" live={false}>{section.data.disclaimer}</Alert>
      <Link to={section.route}>페르소나·검증 계획 자세히 보기</Link>
    </ReportSection>
  );
}

function FinancialSection({ section }) {
  if (!section.data) return <ReportSection id="report-financial" section={section} />;
  let summary; let result;
  try { summary = section.data.summaryJson ? JSON.parse(section.data.summaryJson) : null; } catch { summary = null; }
  try { result = section.data.resultJson ? JSON.parse(section.data.resultJson) : null; } catch { result = null; }
  const base = result?.scenarios?.find((item) => item.code === 'BASE') ?? null;
  return <ReportSection id="report-financial" section={section}>
    <Card className="report-summary-card"><h3>{summary?.headline ?? '재무·수익성 분석 완료'}</h3>{base && <dl className="report-facts"><div><dt>총 매출</dt><dd>{Number(base.totalRevenue).toLocaleString('ko-KR')}원</dd></div><div><dt>총 영업손익</dt><dd>{Number(base.totalOperatingProfit).toLocaleString('ko-KR')}원</dd></div><div><dt>손익분기</dt><dd>{base.breakEvenMonth ? `${base.breakEvenMonth}개월` : '미도달'}</dd></div><div><dt>투자 회수</dt><dd>{base.paybackMonth != null ? `${base.paybackMonth}개월` : '미도달'}</dd></div><div><dt>필요 운영자금</dt><dd>{Number(base.requiredWorkingCapital).toLocaleString('ko-KR')}원</dd></div></dl>}<p>{summary?.breakEvenSummary}</p><p>{summary?.cashRiskSummary}</p>{summary?.sensitiveAssumptions?.length > 0 && <><h4>민감한 가정</h4><ul>{summary.sensitiveAssumptions.map((item) => <li key={item}>{item}</li>)}</ul></>}{summary?.keyRisks?.length > 0 && <><h4>주요 위험</h4><ul>{summary.keyRisks.map((item) => <li key={item}>{item}</li>)}</ul></>}<p>{summary?.disclaimer}</p></Card>
    <Link to={section.route}>재무 분석 상세 보기</Link>
  </ReportSection>;
}

function ValidationTasks({ report }) {
  return (
    <section className="report-section" id="report-validation" aria-labelledby="report-validation-title">
      <div className="report-section__heading">
        <div><p>실제 검증 필요</p><h2 id="report-validation-title">검증 과제</h2></div>
        <Badge tone="warning">{report.validationTasks.length}건</Badge>
      </div>
      {report.validationTasks.length === 0 ? (
        <Card><p>현재 결과에서 생성된 검증 과제가 없습니다.</p></Card>
      ) : (
        <div className="report-section-list">
          {report.validationTasks.map((task) => (
            <Card key={task.key}>
              <div className="report-item-heading"><h3>{task.title}</h3><StatusBadge status={task.priority} /></div>
              <p>{task.reason}</p>
              <dl className="report-facts">
                <div><dt>출처</dt><dd>{task.source}</dd></div>
                <div><dt>검증 방법</dt><dd>{task.method}</dd></div>
                <div><dt>기대 증거</dt><dd>{task.expectedEvidence}</dd></div>
              </dl>
            </Card>
          ))}
        </div>
      )}
      <p className="report-note">이 화면에서는 과제를 완료 처리하지 않습니다. 실제 검증 결과가 입력되기 전까지 계획 상태입니다.</p>
    </section>
  );
}

function Provenance({ report }) {
  return (
    <section className="report-section report-provenance" id="report-sources" aria-labelledby="report-sources-title">
      <div className="report-section__heading">
        <div><p>데이터 계보</p><h2 id="report-sources-title">출처와 생성 정보</h2></div>
      </div>
      <dl className="report-facts">
        <div><dt>프로젝트 버전</dt><dd>{report.project.version}</dd></div>
        <div><dt>원본 문서 버전</dt><dd>{report.sourceDocumentVersionId ?? '정보 없음'}</dd></div>
        <div><dt>구조화 계획 버전</dt><dd>{report.structuredPlanVersion ?? '정보 없음'}</dd></div>
      </dl>
      <div className="report-source-list">
        {report.provenance.map((item) => (
          <Card key={item.section}>
            <h3>{item.section}</h3>
            <p>{item.isMock ? 'Mock provider' : item.provider} · {item.model}</p>
            <small>{item.promptVersion} · {item.completedAt}</small>
          </Card>
        ))}
      </div>
      <Alert title="보고서 사용 한계" tone="warning" live={false}>
        <ul>{report.limitations.map((item) => <li key={item}>{item}</li>)}</ul>
      </Alert>
    </section>
  );
}

export default function ReportPage() {
  const { project } = useProjectContext();
  const state = useIntegratedReport(project);
  if (state.status === 'loading') return <LoadingState label="현재 분석 결과를 통합하고 있습니다" />;
  if (state.status === 'error') {
    return <ErrorState title="통합 보고서를 불러오지 못했습니다" description={state.error?.message} onRetry={state.retry} />;
  }
  const { report } = state;
  return (
    <article
      className="integrated-report"
      aria-labelledby="integrated-report-title"
      role="document"
    >
      <PageHeader
        eyebrow="현재 API 결과의 실시간 조합"
        title={`${project.name} 통합 분석 보고서`}
        description={report.reportStatusLabel}
        actions={(
          <>
            <Button variant="outline" onClick={() => openReportPrintWindow(report)}>인쇄 / PDF 저장</Button>
            <Button onClick={() => downloadReportMarkdown(report)}>Markdown 다운로드</Button>
          </>
        )}
      />
      <p className="report-export-guide">
        PDF가 필요하면 인쇄 창의 대상 또는 프린터에서 ‘PDF로 저장’을 선택하세요.
      </p>
      <span className="visually-hidden" id="integrated-report-title">통합 분석 보고서</span>
      {report.anyMock && <Alert title="Mock 결과 포함" tone="warning">이 보고서에는 실제 외부 AI 호출이 아닌 Mock provider 결과가 포함됩니다.</Alert>}
      <Card className="report-header-card">
        <div><span>보고서 기준</span><strong>{report.generatedAtLabel}</strong></div>
        <div><span>프로젝트 최근 수정</span><strong>{report.projectUpdatedAtLabel}</strong></div>
        <div><span>출처 문서</span><strong>{report.sourceDocumentVersionId ?? '준비 전'}</strong></div>
        <div><span>구조화 버전</span><strong>{report.structuredPlanVersion ?? '준비 전'}</strong></div>
      </Card>
      <nav className="report-local-nav" aria-label="보고서 목차">
        <a href="#report-structure">사업계획</a><a href="#report-legal">법률</a>
        <a href="#report-feasibility">타당성</a><a href="#report-persona">페르소나</a>
        <a href="#report-validation">검증 과제</a><a href="#report-sources">출처</a>
      </nav>
      <section aria-labelledby="report-progress-title">
        <h2 id="report-progress-title">분석 진행 상태</h2>
        <div className="report-progress-grid">
          {report.sections.map((section) => <ReportStatusCard key={section.title} section={section} compact />)}
        </div>
      </section>
      <StructuredPlanSection section={report.plan} />
      <LegalSection section={report.legal} />
      <FeasibilitySection section={report.feasibility} />
      <FinancialSection section={report.financial} />
      <PersonaSection section={report.persona} />
      <ValidationTasks report={report} />
      <Provenance report={report} />
      <ReportPrintDocument report={report} />
    </article>
  );
}
