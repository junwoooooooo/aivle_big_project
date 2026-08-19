import { Badge } from '../../shared/ui';
import Emphasis from './emphasis.jsx';
import {
  CANVAS_LAYOUT, CELL_DOT, CELL_STATUS_VIEW, SOURCE_KIND_VIEW,
  formatValue, gradeView, hostOf,
} from './marketResult.js';

/**
 * BM 캔버스 9칸 요약 격자 — <b>3×3 균등 배치.</b>
 *
 * <p>배치와 순서의 정본은 와이어프레임 `public/wireframe.html` 이다. 예전의 4·3·2 밴드와
 * 밴드 제목(「고객과 가치」 등)은 2026-08-13 에 뗐다.
 *
 * <p>요약 칸은 <b>첫 줄만</b> 보여 준다. 누르면 <b>그 칸 하나만</b> 아래에 펴진다.
 *
 * <p>⚠ 2026-08-13 에는 세부를 통째로 뺐었다(같은 근거표가 9칸 × 전 항목으로 되풀이돼
 * 화면이 길어졌다). <b>그 이유는 지금도 유효하므로 「전부 펴기」로 되돌리지 않는다</b> —
 * 되살린 것은 «누른 칸 하나»뿐이다. 그러지 않으면 캔버스가 「채널 · 근거 12건」이라고
 * 말하면서 <b>그 12건이 뭔지 열어볼 길이 없는</b> 상태가 된다. 배지만 주고 근거를 못 보이면
 * 그것이 곧 「믿으라」는 말이다.
 *
 * <p>`onSelect` 를 안 주면 예전처럼 <b>누를 수 없는</b> 칸이 된다 — 착지할 자리가 없는데
 * 누를 수 있게 두면 눌러도 아무 일이 없어 고장으로 읽힌다.
 */
export default function BmCanvas({ cells, active = null, onSelect = null }) {
  const byCell = new Map(cells.map((cell) => [cell.cell, cell]));
  return (
    <>
      <Tally cells={cells} />
      <div className="bm-canvas">
        {CANVAS_LAYOUT.map(({ cell: key }) => {
          const cell = byCell.get(key);
          if (!cell) return null;
          return (
            <SummaryCell key={key} cell={cell} onSelect={onSelect}
              open={active === cell.cell} />
          );
        })}
      </div>
    </>
  );
}

/**
 * 칸별 세부 — 내용 전체 · 사유 · 경계 · 근거 · 못 찾은 것.
 *
 * ⚠ 칸에 `caveats` 가 있으면 <b>경계를 반드시 함께 그린다.</b> 그것 없이 상태만 보이면
 * 「근거 있음」이 무조건적 확인으로 읽힌다.
 */
export function BmCellDetails({ cells, active }) {
  return (
    <>
      <h3 className="bm-dets__title">칸별 세부</h3>
      <div className="bm-dets">
        {cells.map((cell) => <CellDetail key={cell.cell} cell={cell} active={active === cell.cell} />)}
      </div>
    </>
  );
}

/**
 * 「3칸만 찼다」로 읽히지 않게 **두 종류를 따로 센다.**
 * 계획 칸은 애초에 조사 대상이 아니라 근거 0 이 정상이다.
 */
function Tally({ cells }) {
  const observed = cells.filter((cell) => cell.kind === '관측');
  const planned = cells.filter((cell) => cell.kind === '계획');
  return (
    <p className="bm-tally">
      <span>
        <b className="bm-kind bm-kind--obs">관측</b> {observed.length}칸 중{' '}
        <b>{observed.filter((cell) => cell.evidenceIds.length > 0).length}칸</b> 근거 확보
      </span>
      <span>
        <b className="bm-kind bm-kind--plan">계획</b> {planned.length}칸 중{' '}
        <b>{planned.filter((cell) => cell.content.length > 0).length}칸</b> 서술됨
        {' — 이 칸들은 조사 대상이 아니에요'}
      </span>
    </p>
  );
}

/**
 * 칸의 상태 한 마디.
 *
 * <p>⚠ <b>서버 상태를 «계획» 이라는 이유로 덮어쓰지 않는다.</b> 예전에는 계획 칸이면
 * 무조건 「서술됨/서술 없음」으로 갈아 끼웠는데, 그러면 `KEY_PARTNERS` 의 `UNVERIFIED`
 * 가 사라진다 — 프롬프트가 그 칸만 <b>일부러</b> 다르게 정해 둔 것이다
 * (`bm/prompt.py`: 파트너는 비면 PLAN 이 아니라 UNVERIFIED).
 * 「말한 적이 없다(PLAN)」와 「찾아봤는데 없다(UNVERIFIED)」는 다른 사건이다.
 */
function statusOf(cell) {
  if (cell.kind === '계획' && cell.status === 'PLAN') {
    return cell.content.length > 0 ? '서술됨' : '서술 없음';
  }
  return CELL_STATUS_VIEW[cell.status]?.label ?? cell.status;
}

/**
 * 빈 칸에 <b>할 말은 한 번만</b> 한다.
 *
 * <p>예전에는 같은 사실이 세 줄로 나왔다 — 상태 「서술 없음」, 고정 문구 「컨셉 서술에 이
 * 칸 내용이 없다」, 그리고 모델의 `reason` 「입력에 고객 관계 정보가 포함되지 않음」.
 * 네 칸이 비면 그것만 열두 줄이었다. 모델이 쓴 사유가 있으면 <b>그것을 쓴다</b> —
 * 그 칸에 대해 구체적이기 때문이다. 없을 때만 고정 문구로 메운다.
 */
/**
 * 사유가 «없다»는 뜻의 자리채움 문자열.
 *
 * ⚠ 둘이다. 하나는 정규화가 넣고(`marketResult.js:376`), 다른 하나는 **서버가** 넣는다
 * (`serialize.py` 의 `_text(item.reason, '사유 미기재')`). 예전에는 앞의 것만 걸러서
 * 유료 실행 봉투의 「사유 미기재」가 **사유인 척 화면에 섰다**(실측 봉투에 그 값이 그대로
 * 들어 있다). 자리채움을 사유로 보이면 사용자는 그것을 읽고 아무것도 못 얻는다.
 */
const NO_REASON = new Set(['사유가 오지 않았다', '사유 미기재', '세부 없음']);

function emptyReason(cell) {
  if (cell.reason && !NO_REASON.has(cell.reason)) return cell.reason;
  return cell.kind === '계획'
    ? '컨셉 서술에 이 칸 내용이 없어요'
    : '조사에서 근거를 찾지 못했어요';
}

function KindChip({ kind }) {
  return <span className={`bm-kind bm-kind--${kind === '관측' ? 'obs' : 'plan'}`}>{kind}</span>;
}

function SummaryCell({ cell, onSelect = null, open = false }) {
  // 누를 수 있을 때만 `<button>` 이다. `.bm-cell` 규칙이 이미 `font: inherit ·
  // text-align: left · cursor: pointer` 를 들고 있어 그대로 맞는다.
  const Tag = onSelect ? 'button' : 'div';
  const clickable = onSelect
    ? { type: 'button', onClick: () => onSelect(cell.cell), 'aria-expanded': open,
        'aria-controls': `bm-${cell.cell}` }
    : {};
  return (
    <Tag {...clickable}
      className={`bm-cell${cell.content.length > 0 ? '' : ' bm-cell--plan'}`
        + (open ? ' is-open' : '')}>
      <span className="bm-cell__h">
        <h4>{cell.label}</h4>
        <span className={`bm-dot bm-dot--${CELL_DOT[cell.status] ?? 'none'}`} />
      </span>
      {/* 첫 줄만. 빈 칸에서는 **왜 비었는지**를 그 자리에 세운다.
          ⚠ 예전에는 아무것도 안 그렸다. 그래서 `analyze.validate_canvas_source_labels` 가
          사용자가 쓴 계획 문장을 지웠을 때(실측: 성공 3회 중 2회) 화면에는 「서술 없음」만
          남고 **왜 사라졌는지가 어디에도 없었다.** 사유를 쓰는 함수는 이미 있었는데
          호출부가 0곳인 `BmCellDetails` 안에만 있었다. */}
      {cell.content.length > 0 ? (
        <span className="bm-cell__lead"><Emphasis text={cell.content[0]} /></span>
      ) : (
        <span className="bm-cell__lead bm-cell__why"><Emphasis text={emptyReason(cell)} /></span>
      )}
      {/* ⚠ **경계는 도장과 같은 칸에 선다.** 봉투가 칸마다 `caveats` 를 들고 오는데
          화면이 통째로 버리고 있었다(2026-08-15 실측). 그래서 「가치 제안 — 일부 확인 ·
          근거 3」 이 떴는데 그 근거 3장이 **경쟁사 전사 매출**이라는 말은 어디에도 없었고,
          「고객 세그먼트 — 확인됨」의 근거가 **상위 범주 수**라는 말도 없었다.
          사업가는 도장을 먼저 읽는다 — 경계가 봉투에만 있으면 지운 것과 같다.
          여기는 한 줄 요약 칸이라 **첫 문장만** 세우고, 여러 개면 몇 개인지 적는다. */}
      {/* ★ 판 ㊻ — **경계 «문장»은 타일에서 내리고, 있다는 «표시»만 남긴다.**
          (2026-08-16 사용자 지시)
          왜 문장을 내리나 — 아홉 칸이 모두 두세 줄짜리 경고를 이고 있어서 캔버스가
          경고 벽이 됐고, 그러면 **어느 칸이 진짜 위험한지 못 고른다.** 경고가 흔해지면
          경고가 아니다.
          ⚠ **지우는 것이 아니다.** 문장 전문은 「자세히」(`BmCellDetails`)에 그대로 있고,
          여기에는 **몇 건인지**가 남아 누를 이유가 된다. 경계가 있다는 사실 자체는
          타일에서 사라지지 않는다 — 사라지면 도장만 읽고 넘어간다. */}
      {/* 판 ㊻ — **타일에서 경계 표시를 통째로 뺐다**(2026-08-16 사용자 지시 두 번).
          ⚠ **잃는 것을 적어 둔다.** 전문은 「자세히」(`BmCellDetails`)에 그대로 있지만,
            타일만 보는 사람에게는 도장(`확인됨`·`일부 확인`)만 남는다 — 그 도장이 무엇을
            근거로 찍혔는지(상위 범주 수인지·경쟁사 전사 매출인지)는 눌러야 보인다.
            판 ㊸ 에 이 병을 한 번 고친 기록이 있다. 되살릴 때 그 주석을 볼 것. */}
      <span className="bm-cell__foot">
        <KindChip kind={cell.kind} />
        {statusOf(cell)}
        {/* 「근거 12」라고만 적고 열 길이 없으면 그건 「믿으라」는 말이다. 누를 수 있을 때는
            그렇다고 말해 준다 — 누를 수 있는지가 화면에 안 보이면 아무도 안 누른다. */}
        {cell.evidenceIds.length > 0 ? ` · 근거 ${cell.evidenceIds.length}` : ''}
        {onSelect ? <span className="bm-cell__more">{open ? '닫기' : '자세히'}</span> : null}
      </span>
    </Tag>
  );
}

function CellDetail({ cell, active }) {
  const dot = CELL_DOT[cell.status] ?? 'none';
  const tone = dot === 'ok' ? 'success' : dot === 'mid' ? 'warning' : 'neutral';

  return (
    <section id={`bm-${cell.cell}`} className={`bm-det${active ? ' is-on' : ''}`}>
      <div className="bm-det__h">
        <span className={`bm-dot bm-dot--${dot}`} />
        <h4>{cell.label}</h4>
        <KindChip kind={cell.kind} />
        <span className="bm-det__o">{cell.origin}</span>
        <Badge tone={tone}>{statusOf(cell)}</Badge>
      </div>
      <div className="bm-det__b">
        {/* 칸이 아예 안 온 것과 «근거 필요» 는 다른 사건이다. */}
        {cell.absent ? (
          <p className="bm-cell__none">이 칸이 결과에 오지 않았어요 — 「근거 필요」와 다른 일이에요.</p>
        ) : null}

        {/* 내용이 있으면 내용과 사유를 나란히, 없으면 **사유 한 줄만.**
            빈 칸에 사유를 두 번 세 번 다르게 적으면 읽을 것이 없는데 자리만 커진다. */}
        {cell.content.length > 0 ? (
          <>
            <ul className="bm-det__c">
              {cell.content.map((line) => <li key={line}><Emphasis text={line} /></li>)}
            </ul>
            <p className="bm-det__r"><Emphasis text={cell.reason} /></p>
          </>
        ) : (
          <p className="bm-cell__none"><Emphasis text={emptyReason(cell)} /></p>
        )}

        {/* 경계는 접지 않는다. 값과 같은 화면에 있어야 도달한 것이다. */}
        {cell.caveats.map((caveat) => (
          <p key={caveat} className="mr-caveat"><Emphasis text={caveat} /></p>
        ))}

        {cell.evidence.length > 0 ? (
          <table className="mr-table bm-det__t">
            <tbody>
              {cell.evidence.map((item) => (
                <tr key={item.id}>
                  <td className="v num">{formatValue(item.value, item.unit)}</td>
                  <td>{item.subject} · {item.metric}</td>
                  <td className="p num">{item.period ?? '—'}</td>
                  <td><GradeBadge grade={item.grade} /></td>
                  <td className="s"><SourceLink item={item} /></td>
                </tr>
              ))}
            </tbody>
          </table>
        ) : null}

        {cell.missingEvidence.length > 0 ? (
          <p className="bm-det__m">
            <b>찾지 못한 것</b>{cell.missingEvidence.join(' · ')}
          </p>
        ) : null}
      </div>
    </section>
  );
}

/** ⚠ 등급 누락을 조용히 넘기지 않는다 — 빈 자리는 「확정」으로 읽힌다. */
export function GradeBadge({ grade }) {
  const view = gradeView(grade);
  return <Badge tone={view.tone}>{view.label}</Badge>;
}

export function SourceLink({ item }) {
  const host = hostOf(item.sourceUrl);
  const kind = item.sourceKind ? (SOURCE_KIND_VIEW[item.sourceKind] ?? item.sourceKind) : null;
  return (
    <>
      {item.sourceUrl && host
        ? <a href={item.sourceUrl} target="_blank" rel="noreferrer">{host}</a>
        : '출처 없음'}
      {kind ? <div className="p mr-src-kind">{kind}</div> : null}
    </>
  );
}
