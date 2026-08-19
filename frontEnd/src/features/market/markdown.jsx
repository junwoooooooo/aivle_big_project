import { memo } from 'react';

/**
 * <b>작은 마크다운 렌더러</b> — 봉투 `report.sections[].markdown` 을 그리는 데만 쓴다.
 *
 * <p>⚠ <b>`dangerouslySetInnerHTML` 을 쓰지 않는다.</b> 이 글은 모델이 쓴 것이고
 * 그 안에 출처 링크가 섞여 온다 — HTML 로 부어 넣으면 모델 출력이 곧 DOM 이 된다.
 * 전부 React 요소로 만든다.
 *
 * <p>⚠ <b>라이브러리를 새로 넣지 않는다.</b> 필요한 문법은 이것뿐이다:
 * `###`/`####` 제목 · 문단 · <b>굵게</b> · `[글](링크)` · 표(정렬) · `-` 불릿 · `>` 인용.
 * 모르는 문법은 <b>글자 그대로</b> 남긴다 — 조용히 버리지 않는다.
 */

import { INLINE, SAFE_URL, parseBlocks } from './markdownBlocks.js';

/** 인라인 조각 → React 노드 배열. */
function inlineNodes(text, keyPrefix = 'i') {
  const out = [];
  let last = 0;
  INLINE.lastIndex = 0;
  let match = INLINE.exec(text);
  while (match) {
    if (match.index > last) out.push(text.slice(last, match.index));
    const key = `${keyPrefix}-${match.index}`;
    if (match[1] !== undefined) {
      out.push(<b key={key}>{match[1]}</b>);
    } else if (SAFE_URL.test(match[3])) {
      out.push(
        <a key={key} href={match[3]} target="_blank" rel="noreferrer">{match[2]}</a>,
      );
    } else {
      // 링크로 만들 수 없는 주소는 **글자 그대로** 남긴다 — 사라지면 근거가 사라진다.
      out.push(match[0]);
    }
    last = match.index + match[0].length;
    match = INLINE.exec(text);
  }
  if (last < text.length) out.push(text.slice(last));
  return out;
}

/**
 * 표 칸의 클래스 — <b>목표 HTML(`market-report.html`)의 이름 그대로</b>다.
 * `---:` 은 `num`(오른쪽·자릿수 맞춤), 「출처」 열은 `src`(작은 회색).
 */
function cellClass(align, headText) {
  const source = typeof headText === 'string' && headText.includes('출처');
  return [align === 'r' ? 'num' : align, source ? 'src' : null]
    .filter(Boolean).join(' ') || undefined;
}

/** 글 안의 표를 위에서 몇 행까지 펴 놓을지. 나머지는 접되 **건수를 말한다.** */
const MD_TABLE_ROWS = 6;

function renderBlock(block, index) {
  const key = `b${index}`;
  switch (block.type) {
    case 'heading': {
      // ⚠ 절 제목이 `h2` 다 — 이 글은 «절 안»에 살므로 `###` 은 `h3` 아래로 눌러 둔다.
      const Tag = block.level <= 3 ? 'h3' : 'h4';
      return <Tag key={key}>{inlineNodes(block.text, key)}</Tag>;
    }
    case 'hr':
      return <hr key={key} />;
    case 'list':
      return (
        <ul key={key}>
          {block.items.map((item, n) => <li key={`${key}-${n}`}>{inlineNodes(item, `${key}-${n}`)}</li>)}
        </ul>
      );
    case 'quote':
      return <blockquote key={key}>{block.blocks.map(renderBlock)}</blockquote>;
    case 'table': {
      // ★ 판 ㊻ — **표는 위에서 6행까지만 편다.** (2026-08-16 사용자 지시)
      //
      // 글을 쓰는 프롬프트에도 「최대 6행」을 걸어 뒀지만 **모델은 그 약속을 깬다** —
      // 실측으로 42행·50행짜리 표가 왔다. 화면이 마지막 방벽이다.
      //
      // ⚠ **자른 것을 «숨기지» 않는다.** 몇 행이 더 있는지 적고, 펴면 그대로 다 보인다.
      //   숨기면 사업가는 표가 원래 그만큼인 줄 안다 — 「합이 100인 표가 절반만 보이면
      //   1위가 뒤바뀐다」와 같은 병이다.
      const 앞 = block.rows.slice(0, MD_TABLE_ROWS);
      const 뒤 = block.rows.slice(MD_TABLE_ROWS);
      const 표 = (rows, k) => (
        <div key={k} className="tw">
          <table>
            <thead>
              <tr>
                {block.head.map((cell, n) => (
                  <th key={`${k}-h${n}`} className={cellClass(block.aligns[n], cell)}>
                    {inlineNodes(cell, `${k}-h${n}`)}
                  </th>
                ))}
              </tr>
            </thead>
            <tbody>
              {rows.map((row, r) => (
                <tr key={`${k}-r${r}`}>
                  {row.map((cell, n) => (
                    <td key={`${k}-r${r}c${n}`} className={cellClass(block.aligns[n], block.head[n])}>
                      {inlineNodes(cell, `${k}-r${r}c${n}`)}
                    </td>
                  ))}
                </tr>
              ))}
            </tbody>
          </table>
        </div>
      );
      if (뒤.length === 0) return 표(block.rows, key);
      return (
        <div key={key}>
          {표(앞, `${key}-a`)}
          <details className="md-more">
            <summary>이 표의 나머지 {뒤.length}행 더 보기</summary>
            {표(뒤, `${key}-b`)}
          </details>
        </div>
      );
    }
    default:
      return <p key={key}>{inlineNodes(block.text, key)}</p>;
  }
}

/**
 * <b>마크다운 한 덩이.</b> `text` 가 문자열 하나뿐이라 `memo` 로 재파싱을 막는다 —
 * 절을 접었다 펼 때마다 수천 자를 다시 훑을 이유가 없다.
 */
const Markdown = memo(function Markdown({ text, className = 'md' }) {
  if (typeof text !== 'string' || !text.trim()) return null;
  return <div className={className}>{parseBlocks(text).map(renderBlock)}</div>;
});

export default Markdown;
