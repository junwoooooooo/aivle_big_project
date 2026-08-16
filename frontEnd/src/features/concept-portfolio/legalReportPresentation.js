const asList = (value) => Array.isArray(value) ? value : value == null || value === '' ? [] : [value];

const legalItemText = (value) => (typeof value === 'string'
  ? value
  : value?.safeSummary ?? value?.title ?? '');

const normalizedLegalText = (value) => String(legalItemText(value) ?? '').trim().replace(/\s+/g, ' ');

/**
 * 사용자에게 같은 의무 문장이 반복되지 않도록 만드는 보수적인 presentation key다.
 * 문장 내부는 손대지 않고, 끝 문장부호와 제한된 의무 종결형만 제거한다.
 */
export function legalPresentationKey(value) {
  let key = normalizedLegalText(value).replace(/[.!?。！？]+$/u, '').trim();
  const obligationEndings = [
    /(?:이|가)\s*필요(?:함|합니다)$/u,
    /\s*필요(?:함|합니다)$/u,
    /\s*해야\s*(?:함|합니다)$/u,
    /\s*요구됨$/u,
  ];
  for (const ending of obligationEndings) {
    const normalized = key.replace(ending, '').trim();
    if (normalized !== key) {
      key = normalized;
      break;
    }
  }
  return key;
}

export function uniqueLegalItems(...sources) {
  const unique = new Map();
  for (const item of sources.flatMap(asList)) {
    const key = legalPresentationKey(item);
    if (key && !unique.has(key)) unique.set(key, item);
  }
  return [...unique.values()];
}

export function excludeLegalItems(values, ...ownedSources) {
  const ownedKeys = new Set(uniqueLegalItems(...ownedSources).map(legalPresentationKey));
  return uniqueLegalItems(values).filter((item) => !ownedKeys.has(legalPresentationKey(item)));
}

export function legalAttentionGroups(reportBody = {}) {
  return [
    { title: '반드시 해야 할 조치', values: uniqueLegalItems(reportBody.requiredControls) },
    { title: '필수 고지', values: uniqueLegalItems(reportBody.requiredDisclosures) },
    { title: '파트너·자격·인허가', values: uniqueLegalItems(reportBody.partnerRequirements, reportBody.qualificationRequirements, reportBody.requiredPartnersAndQualifications) },
    { title: '아직 확인되지 않은 사항', values: uniqueLegalItems(reportBody.unknownFacts), meaningfulWhenEmpty: true },
  ];
}

export function advertisingOnlyDisclosures(reportBody = {}) {
  const generalKeys = new Set(uniqueLegalItems(reportBody.requiredDisclosures).map(legalPresentationKey));
  return uniqueLegalItems(reportBody.advertisingExpressionCautions?.requiredDisclosures)
    .filter((item) => !generalKeys.has(legalPresentationKey(item)));
}

export function legalReportSummaryCounts(reportBody = {}) {
  const groups = legalAttentionGroups(reportBody);
  return {
    controls: groups[0].values.length,
    disclosures: groups[1].values.length,
    partners: groups[2].values.length,
    unknownFacts: groups[3].values.length,
  };
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
