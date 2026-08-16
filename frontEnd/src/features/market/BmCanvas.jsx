import { Badge } from '../../shared/ui';
import Emphasis from './emphasis.jsx';
import {
  CANVAS_BANDS, CELL_DOT, CELL_STATUS_VIEW, SOURCE_KIND_VIEW,
  formatValue, gradeView, hostOf,
} from './marketResult.js';

/**
 * BM 캔버스 9칸 요약 격자.
 *
 * <p>격자는 <b>4·3·2 세 묶음</b>이다. 표준 5열 배치는 포스터 판형이라 본문 폭에서 칸당
 * 195px 밖에 안 나오고, 한글이 10~12자마다 끊긴다(사유는 `CANVAS_BANDS` 주석).
 *
 * <p>요약 칸은 <b>첫 줄만</b> 보여 주고 나머지는 `BmCellDetails` 에 둔다. 칸을 누르면 그
 * 세부로 착지한다 — 그 연결이 없으면 칸의 문장이 <b>출처 없는 단정</b>으로 읽힌다.
 */
export default function BmCanvas({ cells, onJump }) {
  const byCell = new Map(cells.map((cell) => [cell.cell, cell]));
  return (
    <>
      <Tally cells={cells} />
      <div>
        {CANVAS_BANDS.map(([title, keys]) => (
          <div key={title} className={`bm-band bm-band--${keys.length}`}>
            <div className="bm-band__t">{title}</div>
            <div className="bm-band__row">
              {keys.map((key) => {
                const cell = byCell.get(key);
                return cell ? <SummaryCell key={key} cell={cell} onJump={onJump} /> : null;
              })}
            </div>
          </div>
        ))}
      </div>
    </>
  );
}

/**
 * 칸별 세부 — 내용 전체 · 사유 · 경계 · 근거 · 못 찾은 것.
 *
 * ⚠ 칸에 `caveats` 가 있으면 <b>경계를 반드시 함께 그린다.</b> 그것 없이 상태만 보이면
 * 「확인됨」이 무조건적 확인으로 읽힌다.
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
        {' — 이 칸들은 조사 대상이 아니다'}
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
function emptyReason(cell) {
  if (cell.reason && cell.reason !== '사유가 오지 않았다') return cell.reason;
  return cell.kind === '계획'
    ? '컨셉 서술에 이 칸 내용이 없다'
    : '조사에서 근거를 못 찾았다';
}

function KindChip({ kind }) {
  return <span className={`bm-kind bm-kind--${kind === '관측' ? 'obs' : 'plan'}`}>{kind}</span>;
}

function SummaryCell({ cell, onJump }) {
  return (
    <button
      type="button"
      className={`bm-cell${cell.content.length > 0 ? '' : ' bm-cell--plan'}`}
      onClick={() => onJump(cell.cell)}
    >
      <span className="bm-cell__h">
        <h4>{cell.label}</h4>
        <span className={`bm-dot bm-dot--${CELL_DOT[cell.status] ?? 'none'}`} />
      </span>
      {/* 첫 줄만 — 나머지는 아래 세부에 있다. 줄 수를 자르지는 않는다.
          ⚠ 빈 칸에서는 **아래 상태 줄이 곧 내용**이다. 여기에 같은 말을 또 쓰면
          한 장짜리 카드가 같은 사실을 두 번 말한다. */}
      {cell.content.length > 0 ? (
        <span className="bm-cell__lead"><Emphasis text={cell.content[0]} /></span>
      ) : null}
      <span className="bm-cell__foot">
        <KindChip kind={cell.kind} />
        {statusOf(cell)}
        {cell.evidenceIds.length > 0 ? ` · 근거 ${cell.evidenceIds.length}` : ''}
      </span>
    </button>
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
        {/* 칸이 아예 안 온 것과 «미확인» 은 다른 사건이다. */}
        {cell.absent ? (
          <p className="bm-cell__none">이 칸이 결과에 오지 않았다 — 「미확인」과 다른 사건이다.</p>
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
            <b>못 찾은 것</b>{cell.missingEvidence.join(' · ')}
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
