import { Badge } from '../../shared/ui';
import Emphasis from './emphasis.jsx';
import { abbreviateKrw, formatValue } from './marketResult.js';

/**
 * 가정 원장 — <b>계산식의 해부도.</b>
 *
 * <p>이 제품의 정체는 「관측 / 가정」의 분리 하나다. 그 구분이 산문 안에만 있으면 읽는
 * 사람은 결국 숫자만 본다. 그래서 여기서는 <b>공식을 표의 머리로 놓고 항을 한 줄씩</b>
 * 편다 — 판정이 문장이 아니라 <b>표의 한 열</b>이 된다.
 *
 * <p>바꾸기 전에는 이랬다: 경고 여섯 개를 ` · ` 로 이어 붙인 한 문단, 그 안에 날것의
 * `**` 와 「원 근거 서술: … 두발 미」처럼 100자에서 잘린 문장. 표가 없어서가 아니라
 * <b>표로 펼 재료를 문장으로 뭉개서</b> 그랬다.
 *
 * <p>⚠ <b>화면은 아무것도 판정하지 않는다.</b> `basis` 도 `sourceCount` 도 서버가 정한
 * 값을 그대로 그린다. 여기서 값을 보고 다시 가르면 두 구현이 갈라진다.
 *
 * <p>⚠ <b>경계 문장을 접지 않는다.</b> 규칙상 지울 수도 없고 숨길 수도 없다 —
 * 표로 옮기는 것이지 없애는 것이 아니다.
 */
const BASIS_TONE = { 관측: 'success', 가정: 'warning', 가설: 'info' };

/**
 * 항의 값. **단위를 붙이되 「0.19 비율」처럼 읽히지 않게** 한다 —
 * 비율은 단위 이름이 값을 설명하지 못하고 자리만 먹는다.
 * ⚠ 값 자체는 손대지 않는다(0.19 를 19% 로 바꾸지 않는다). 화면이 값을 고치면
 * 원장의 수와 화면의 수가 갈라진다.
 */
function factorValue({ value, unit }) {
  if (value === null) return '—';
  // ⚠ 항의 값은 **줄이지 않는다.** 39,000원이 「3.9만원」이 되면 단가가 안 읽힌다.
  //   줄여 쓰는 자리는 결론 한 줄(합계)뿐이다.
  if (unit === '원' || unit === 'KRW') return formatValue(value, '원');
  if (!unit || unit === '비율') return value.toLocaleString('ko-KR');
  return formatValue(value, unit);
}

/**
 * 표 아래 각주 한 줄.
 * ⚠ **원문이 없으면 줄 자체를 만들지 않는다.** 렌더 결과로 판단하면 안 된다 —
 *   `<Emphasis text={null}/>` 는 아무것도 안 그리지만 **엘리먼트라서 truthy** 다.
 *   그래서 내용 없는 「관측된 울타리」·「반증 조건」 딱지만 줄줄이 섰다(실측).
 */
function Note({ label, text }) {
  if (!text) return null;
  return (
    <div className="mr-fact__note">
      <b>{label}</b>
      <span><Emphasis text={text} /></span>
    </div>
  );
}

function FactorRow({ factor }) {
  const observed = factor.basis === '관측';

  return (
    <tr className={observed ? '' : 'is-assumed'}>
      <td className="mr-fact__name">{factor.name}</td>
      <td className="v num">{factorValue(factor)}</td>
      <td><Badge tone={BASIS_TONE[factor.basis] ?? 'neutral'}>{factor.basis}</Badge></td>
      <td>
        {factor.note ? (
          <div className="mr-fact__why"><Emphasis text={factor.note} /></div>
        ) : null}
        {/* 울타리는 「가정이지만 이 선은 못 넘는다」 — 관측이다. 가정과 같은 칸에 두면
            둘이 구분되지 않으므로 이름을 붙여 따로 세운다. */}
        <Note label="관측된 울타리" text={factor.bound} />
        <Note label="반증 조건" text={factor.falsifiedIf} />
        {/* 건수와 화자 수는 다른 수다. 3건이 한 도메인에서 나오면 3중이 아니라 1중이다. */}
        {observed ? (
          <div className="mr-fact__src num">
            출처 {factor.sourceDomains.length || '?'}곳 · {factor.sourceCount}건
            {factor.sourceDomains.length ? ` — ${factor.sourceDomains.join(', ')}` : ''}
          </div>
        ) : null}
        {factor.caveats.map((line) => (
          <div key={line} className="mr-caveat"><Emphasis text={line} /></div>
        ))}
      </td>
    </tr>
  );
}

/** 한 추정(TAM·SAM·성장률)의 원장 한 장. */
function FigureLedger({ title, figure }) {
  if (!figure) return null;
  const assumed = figure.factors.filter((f) => f.basis !== '관측').length;
  const total = figure.unit === 'KRW'
    ? (abbreviateKrw(figure.value) ?? formatValue(figure.value, figure.unit))
    : formatValue(figure.value, figure.unit);

  return (
    <div className="mr-ledger">
      <div className="mr-ledger__h">
        <h4>{title}</h4>
        {/* 공식이 표의 머리다 — 항이 어디서 왔는지 눈으로 잇게. */}
        {figure.formula ? <code>{figure.formula}</code> : null}
      </div>

      {figure.factors.length > 0 ? (
        <table className="mr-table mr-ledger__t">
          <thead>
            <tr><th>항</th><th>값</th><th>판정</th><th>근거</th></tr>
          </thead>
          <tbody>
            {figure.factors.map((factor) => (
              <FactorRow key={factor.name} factor={factor} />
            ))}
          </tbody>
          <tfoot>
            <tr>
              <td className="mr-fact__name">= {title}</td>
              <td className="v num">{total}</td>
              <td colSpan={2}>
                {assumed > 0
                  ? `가정이 ${assumed}개 곱해진 추정이다`
                  : '모든 항이 관측이다'}
              </td>
            </tr>
          </tfoot>
        </table>
      ) : null}

      {/* 표가 말할 수 없는 것 — 항이 아니라 «읽는 법»에 붙는 문장이다. */}
      {figure.assumptions.length > 0 ? (
        <ul className="mr-ledger__reads">
          {figure.assumptions.map((line) => (
            <li key={line}><Emphasis text={line} /></li>
          ))}
        </ul>
      ) : null}
    </div>
  );
}

/**
 * 값을 오독하게 만드는 것을 모은다.
 * ⚠ **규칙상 지울 수 없다** — 이것이 빠지면 추정이 확정으로 읽힌다.
 */
export default function AssumptionLedger({ market }) {
  const ledgers = [['TAM', market.tam], ['SAM', market.sam], ['연 성장률', market.growth]]
    .filter(([, figure]) => figure && (figure.factors.length > 0 || figure.assumptions.length > 0));

  const footnotes = [];
  if (market.price?.baseNote) footnotes.push(market.price.baseNote);
  if (!market.som) footnotes.push('SOM 은 산출하지 않았다 — 0 이 아니라 «안 쟀다»다.');

  if (ledgers.length === 0 && footnotes.length === 0) return null;

  return (
    <section className="mr-limits">
      <span>이 숫자를 읽는 조건</span>
      {ledgers.map(([title, figure]) => (
        <FigureLedger key={title} title={title} figure={figure} />
      ))}
      {footnotes.length > 0 ? (
        <ul className="mr-limits__foot">
          {footnotes.map((line) => <li key={line}><Emphasis text={line} /></li>)}
        </ul>
      ) : null}
    </section>
  );
}
