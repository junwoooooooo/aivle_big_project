import { buildDiffBanner, rerunSummary } from '../model/legalReviewViewModel.js';

/** 재검토 완료 후 diff 요약: "해결 1 · 신규 0 · 유지 5" (+ 재실행/승계 범주). */
export function DiffBanner({ diff, rerunCategories, carriedCategories }) {
  const banner = buildDiffBanner(diff);
  if (!banner) return null;
  const rerun = rerunSummary(rerunCategories, carriedCategories);
  return (
    <div className="legal-diff-banner" role="status">
      <strong>재검토 결과</strong>
      <span className="legal-diff-banner__counts">{banner}</span>
      {rerun && <span className="legal-muted">{rerun}</span>}
    </div>
  );
}
