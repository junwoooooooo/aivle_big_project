const AXIS_GROUPS = Object.freeze([
  { id: 'barriers', title: '왜 망설이거나 안 산다고 하나요', axes: ['BARRIER', 'CONCERN'] },
  { id: 'suggestions', title: '어떤 개선을 원하나요', axes: ['SUGGESTION'] },
  { id: 'usage', title: '언제·어디서 사용하나요', axes: ['USAGE_SCENE'] },
  { id: 'value', title: '좋았던 점과 차별점은 무엇인가요', axes: ['LIKE', 'DIFFERENTIATION'] },
]);

const text = (value) => typeof value === 'string' && value.trim() ? value.trim() : null;
const count = (value) => Number.isInteger(value) && value >= 0 ? value : null;
const ids = (value) => Array.isArray(value)
  ? [...new Set(value.filter((item) => typeof item === 'string' && item.trim()).map((item) => item.trim()))]
  : [];

function stableRank(items) {
  return [...items].sort((left, right) => (right.mentionCount ?? -1) - (left.mentionCount ?? -1)
    || left.order - right.order || left.key.localeCompare(right.key, 'ko'));
}

function normalizeThemes(result) {
  return (Array.isArray(result?.themes) ? result.themes : []).map((theme, order) => ({
    key: `${text(theme?.axis) ?? 'UNKNOWN'}:${text(theme?.title) ?? order}`,
    order, axis: text(theme?.axis), title: text(theme?.title), description: text(theme?.description),
    quote: text(theme?.quote), participantIds: ids(theme?.participantIds),
    mentionCount: count(theme?.mentionCount), targetCount: count(theme?.targetCount),
    nonTargetCount: count(theme?.nonTargetCount),
  })).filter((theme) => theme.title);
}

function normalizeAlternatives(result, startOrder) {
  const groups = new Map();
  (Array.isArray(result?.codingTrace) ? result.codingTrace : []).forEach((trace, index) => {
    const label = text(trace?.alternativeLabel);
    const participantId = text(trace?.participantId);
    if (!label || !participantId) return;
    const item = groups.get(label) ?? { key: `ALTERNATIVE:${label}`, order: startOrder + index,
      axis: 'ALTERNATIVE', title: label, description: null, quote: null, participantIds: [],
      mentionCount: 0, targetCount: 0, nonTargetCount: 0 };
    if (!item.participantIds.includes(participantId)) {
      item.participantIds.push(participantId); item.mentionCount += 1;
      if (trace?.group === 'TARGET') item.targetCount += 1;
      else if (trace?.group === 'COMPARISON') item.nonTargetCount += 1;
    }
    groups.set(label, item);
  });
  return [...groups.values()];
}

function headline(label, item, usableCount) {
  if (!item) return null;
  return { label, title: item.title, count: item.mentionCount, total: usableCount,
    key: item.key, participantIds: item.participantIds };
}

export function marketInterviewDashboard(result) {
  const usableCount = count(result?.targeting?.usableCount)
    ?? count(result?.targeting?.drawnSampleSize) ?? 0;
  const baseThemes = normalizeThemes(result);
  const alternativeThemes = normalizeAlternatives(result, baseThemes.length);
  const ranked = stableRank(baseThemes);
  const sections = AXIS_GROUPS.map((section) => ({ ...section,
    themes: stableRank(baseThemes.filter((item) => section.axes.includes(item.axis))) }))
    .filter((section) => section.themes.length);
  if (alternativeThemes.length) sections.splice(Math.min(2, sections.length), 0, {
    id: 'alternatives', title: '지금 무엇으로 해결하나요', axes: ['ALTERNATIVE'],
    themes: stableRank(alternativeThemes),
  });
  const participants = (Array.isArray(result?.participants) ? result.participants : []).map((participant, order) => ({
    order, participantId: text(participant?.participantId), label: text(participant?.label),
    profile: text(participant?.profile), context: text(participant?.context),
    needs: Array.isArray(participant?.needs) ? participant.needs.map(text).filter(Boolean) : [],
    group: participant?.group === 'COMPARISON' ? 'COMPARISON' : 'TARGET',
    interview: (Array.isArray(result?.interviews) ? result.interviews : [])
      .find((item) => item?.participantId === participant?.participantId) ?? null,
  })).filter((participant) => participant.participantId);
  return {
    usableCount,
    headlines: [headline('안 사는 이유', ranked.find((item) => ['BARRIER', 'CONCERN'].includes(item.axis)), usableCount),
      headline('가장 많은 요청', ranked.find((item) => item.axis === 'SUGGESTION'), usableCount),
      headline('지금 쓰는 것', stableRank(alternativeThemes)[0], usableCount)].filter(Boolean),
    sections, themes: [...baseThemes, ...alternativeThemes], participants,
    crossRelationships: Array.isArray(result?.crossRelationships) ? result.crossRelationships : [],
  };
}
