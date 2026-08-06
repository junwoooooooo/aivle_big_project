function text(value, fallback = '정보 없음') {
  if (value === null || value === undefined || value === '') return fallback;
  return String(value)
    .replace(/\r?\n/g, ' ')
    .replace(/&/g, '&amp;')
    .replace(/</g, '&lt;')
    .replace(/>/g, '&gt;')
    .replace(/([\\`*_[\]#])/g, '\\$1')
    .trim();
}

function list(values, map = (value) => value) {
  if (!values?.length) return '- 정보 없음';
  return values.map((value) => `- ${text(map(value))}`).join('\n');
}

export function safeReportFileName(projectName, date = new Date()) {
  const sanitized = String(projectName ?? '')
    .normalize('NFKC')
    .split('')
    .map((character) => (character.charCodeAt(0) < 32 ? '-' : character))
    .join('')
    .replace(/[<>:"/\\|?*]/g, '-')
    .replace(/\.+$/g, '')
    .replace(/\s+/g, '-')
    .replace(/-+/g, '-')
    .replace(/^-|-$/g, '')
    .slice(0, 80) || 'project';
  const stamp = date.toISOString().slice(0, 10);
  return `${sanitized}_사업검증_결과및개선제안서_${stamp}.md`;
}

export function createReportMarkdown(report) {
  const planLines = report.plan.sections.map(
    (section) => `- **${text(section.displayName)}** (${text(section.status)}): ${text(section.extractedContent, '추출 내용 없음')}`,
  );
  const legalLines = report.legal.importantFindings.map(
    (item) => `- **${text(item.categoryLabel)} · ${text(item.riskLevel)}**: ${text(item.finding)} — ${text(item.recommendedAction)}`,
  );
  const dimensionLines = report.feasibility.dimensions.map(
    (item) => `- **${text(item.label)}**: ${text(item.finding)}${item.score == null ? '' : ` (서버 점수 ${item.score})`}`,
  );
  const personaLines = report.persona.items.slice(0, 2).map(
    (item) => `- **${text(item.baselinePersona?.displayName)}**: ${text(item.interpretation)} (적합도 ${text(item.fitScore)})`,
  );

  return [
    '# 사업 검증 결과 및 개선 제안서',
    '',
    '## 프로젝트 정보',
    '',
    `- 프로젝트명: ${text(report.project.name)}`,
    `- 사업 분야: ${text(report.project.industryCategory)}`,
    '',
    `- 생성 기준: ${text(report.generatedAtLabel)}`,
    `- 프로젝트 단계: ${text(report.project.stageLabel)}`,
    `- 보고서 상태: ${text(report.reportStatusLabel)}`,
    `- 원본 문서 버전: ${text(report.sourceDocumentVersionId)}`,
    `- 구조화 계획 버전: ${text(report.structuredPlanVersion)}`,
    '',
    '## 1. 사업계획 구조화 결과',
    '',
    text(report.plan.summary),
    '',
    planLines.length ? planLines.join('\n') : '- 아직 구조화 결과가 없습니다.',
    '',
    '## 2. 법률·규제 검토',
    '',
    text(report.legal.summary),
    '',
    legalLines.length ? legalLines.join('\n') : '- 아직 법률 사전검토 결과가 없습니다.',
    report.legal.data?.disclaimer ? `\n> ${text(report.legal.data.disclaimer)}` : '',
    '',
    '## 3. 사업 타당성 분석',
    '',
    text(report.feasibility.summary),
    '',
    dimensionLines.length ? dimensionLines.join('\n') : '- 아직 사업 타당성 결과가 없습니다.',
    report.feasibility.data?.disclaimer ? `\n> ${text(report.feasibility.data.disclaimer)}` : '',
    '',
    '## 4. 고객 및 시장 검증',
    '',
    text(report.persona.summary),
    '',
    personaLines.length ? personaLines.join('\n') : '- 아직 페르소나 추천 결과가 없습니다.',
    report.persona.data?.disclaimer ? `\n> ${text(report.persona.data.disclaimer)}` : '',
    '',
    '## 5. 권장 다음 행동',
    '',
    list(report.validationTasks, (task) => `${task.title} · ${task.method} · ${task.expectedEvidence}`),
    '',
    '## 출처와 생성 정보',
    '',
    list(report.provenance, (item) => `${item.section}: ${item.isMock ? 'Mock' : item.provider} / ${item.model} / ${item.promptVersion} / ${item.completedAt}`),
    '',
    '## 한계와 주의사항',
    '',
    list(report.limitations),
    '',
  ].join('\n');
}

export function downloadReportMarkdown(report, documentRef = document, urlApi = URL) {
  const content = createReportMarkdown(report);
  const blob = new Blob([`\uFEFF${content}`], { type: 'text/markdown;charset=utf-8' });
  const url = urlApi.createObjectURL(blob);
  const anchor = documentRef.createElement('a');
  anchor.href = url;
  anchor.download = safeReportFileName(report.project.name);
  anchor.click();
  urlApi.revokeObjectURL(url);
}
