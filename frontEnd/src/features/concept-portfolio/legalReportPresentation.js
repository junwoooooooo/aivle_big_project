const asList = (value) => Array.isArray(value) ? value : value == null || value === '' ? [] : [value];

export function legalExecutionGuide(reportBody = {}) {
  const advertising = reportBody.advertisingExpressionCautions ?? {};
  return [
    { title: '필요한 조치', values: asList(reportBody.requiredControls) },
    { title: '고객에게 안내할 내용', values: [...asList(reportBody.requiredDisclosures), ...asList(advertising.requiredDisclosures)] },
    { title: '외부 협의·자격', values: [...asList(reportBody.partnerRequirements), ...asList(reportBody.qualificationRequirements), ...asList(reportBody.requiredPartnersAndQualifications)] },
    { title: '피해야 할 표현', values: asList(reportBody.prohibitedVariants) },
    { title: '추가 확인사항', values: asList(reportBody.unknownFacts) },
  ];
}

function compactTimestamp(value) {
  const match = String(value ?? '').match(/^(\d{4})-(\d{2})-(\d{2})[T\s](\d{2}):(\d{2})/);
  if (match) return `${match[1]}${match[2]}${match[3]}_${match[4]}${match[5]}`;
  const now = new Date();
  const part = (number) => String(number).padStart(2, '0');
  return `${now.getFullYear()}${part(now.getMonth() + 1)}${part(now.getDate())}_${part(now.getHours())}${part(now.getMinutes())}`;
}

export function sanitizeFilenamePart(value, maxLength = 72) {
  const normalized = String(value ?? '선택_사업안')
    .replace(/[<>:"/\\|?*]+/g, ' ')
    .trim()
    .replace(/\s+/g, '_')
    .replace(/[. ]+$/g, '');
  return (normalized || '선택_사업안').slice(0, maxLength);
}

export function legalReportSuggestedFilename(conceptName, generatedAt) {
  return `${sanitizeFilenamePart(conceptName)}_법률규제_사전검토_보고서_${compactTimestamp(generatedAt)}`;
}

export function printLegalReport(conceptName, generatedAt) {
  const originalTitle = document.title;
  const restore = () => { document.title = originalTitle; };
  document.title = legalReportSuggestedFilename(conceptName, generatedAt);
  window.addEventListener('afterprint', restore, { once: true });
  window.print();
}
