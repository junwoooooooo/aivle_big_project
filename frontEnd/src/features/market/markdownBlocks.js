/**
 * <b>보고서 글의 블록 나누기.</b> React 를 안 쓰는 순수 부분이라 그리는 쪽과 갈라 뒀다 —
 * 렌더러 파일이 «부품 말고 다른 것»을 내보내면 lint(`react-refresh`)가 막는다.
 *
 * <p>다루는 문법은 이것뿐이다: `###`/`####` 제목 · 문단 · <b>굵게</b> · `[글](링크)` ·
 * 표(정렬) · `-` 불릿 · `>` 인용. 모르는 문법은 <b>글자 그대로</b> 남긴다.
 */

/** 굵게 · 링크. 한 줄에 둘 다 섞여 오므로 한 정규식으로 훑는다. */
export const INLINE = /\*\*([^*]+)\*\*|\[([^\]]+)\]\(([^)\s]+)\)/g;
/** 모델이 쓴 링크다 — `javascript:` 같은 것이 오면 링크로 만들지 않고 글자로 둔다. */
export const SAFE_URL = /^(https?:\/\/|mailto:|\/)/i;
const HEADING = /^(#{1,6})\s+(.*)$/;
const BULLET = /^\s*[-*]\s+(.*)$/;
const QUOTE = /^\s*>\s?(.*)$/;
const RULE = /^\s*(-{3,}|\*{3,}|_{3,})\s*$/;
const CELL_DASH = /^:?-+:?$/;

function splitRow(line) {
  return line.trim().replace(/^\|/, '').replace(/\|$/, '').split('|')
    .map((cell) => cell.trim());
}

/** 표의 둘째 줄인가 — `|---|---:|` 처럼 칸이 전부 대시로만 돼 있는 줄. */
function isDelimiterRow(line) {
  if (typeof line !== 'string' || !line.includes('-')) return false;
  const cells = splitRow(line);
  return cells.length > 0 && cells.every((cell) => CELL_DASH.test(cell));
}

/** `---:` 오른쪽 · `:---:` 가운데 · 나머지 왼쪽. */
function alignOf(cell) {
  const right = cell.endsWith(':');
  const left = cell.startsWith(':');
  if (right && left) return 'c';
  if (right) return 'r';
  return null;
}

/**
 * 마크다운 → 블록 목록. 렌더와 가르는 이유는 <b>테스트가 구조를 직접 보게</b> 하려는 것이다.
 */
export function parseBlocks(markdown) {
  const lines = String(markdown).replace(/\r\n/g, '\n').split('\n');
  const blocks = [];
  let i = 0;
  while (i < lines.length) {
    const line = lines[i];
    if (!line.trim()) { i += 1; continue; }

    if (RULE.test(line)) { blocks.push({ type: 'hr' }); i += 1; continue; }

    const heading = HEADING.exec(line);
    if (heading) {
      blocks.push({ type: 'heading', level: heading[1].length, text: heading[2].trim() });
      i += 1;
      continue;
    }

    if (line.trim().startsWith('|') && isDelimiterRow(lines[i + 1])) {
      const head = splitRow(line);
      const aligns = splitRow(lines[i + 1]).map(alignOf);
      i += 2;
      const rows = [];
      while (i < lines.length && lines[i].trim().startsWith('|')) {
        rows.push(splitRow(lines[i]));
        i += 1;
      }
      blocks.push({ type: 'table', head, aligns, rows });
      continue;
    }

    if (BULLET.test(line)) {
      const items = [];
      while (i < lines.length && BULLET.test(lines[i])) {
        items.push(BULLET.exec(lines[i])[1]);
        i += 1;
      }
      blocks.push({ type: 'list', items });
      continue;
    }

    if (QUOTE.test(line)) {
      const inner = [];
      while (i < lines.length && QUOTE.test(lines[i])) {
        inner.push(QUOTE.exec(lines[i])[1]);
        i += 1;
      }
      blocks.push({ type: 'quote', blocks: parseBlocks(inner.join('\n')) });
      continue;
    }

    // 문단 — 빈 줄이나 다른 블록이 나올 때까지 이어 붙인다.
    const paragraph = [];
    while (i < lines.length && lines[i].trim()
      && !HEADING.test(lines[i]) && !BULLET.test(lines[i]) && !QUOTE.test(lines[i])
      && !RULE.test(lines[i]) && !lines[i].trim().startsWith('|')) {
      paragraph.push(lines[i].trim());
      i += 1;
    }
    // 표가 아닌 `|` 줄(머리·구분줄이 없는 조각)도 버리지 않고 문단으로 흘린다.
    if (paragraph.length === 0) { paragraph.push(lines[i].trim()); i += 1; }
    blocks.push({ type: 'paragraph', text: paragraph.join(' ') });
  }
  return blocks;
}

