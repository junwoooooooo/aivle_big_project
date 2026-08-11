/**
 * `**강조**` 를 <strong> 으로 바꾸는 **인라인 전용** 분할기.
 *
 * <p>왜 라이브러리를 안 넣나: 이 앱의 런타임 의존성은 react·react-dom·react-router-dom
 * 셋뿐이다. 강조 하나 때문에 마크다운 파서와 살균기를 들이면 그 자세가 깨진다.
 *
 * <p>왜 필요한가: 강조를 쓰는 쪽은 모델이 아니라 <b>규칙 파일</b>이다
 * (`research2/rules/assumptions.v1.json` 의 `basis` 가 「**가정이다 — 관측이 아니다.**」로
 * 시작한다). 규칙의 문구는 정본이라 화면 편의로 고치지 않는다 — 그래서 화면이 읽는다.
 *
 * <p>블록 문법(제목·목록·표)은 <b>일부러</b> 지원하지 않는다. 데이터에 없고, 지원하는
 * 순간 서버가 화면 레이아웃을 문자열로 지시할 수 있게 된다.
 *
 * <p>HTML 을 만들지 않는다 — 조각을 잘라 React 노드로 돌려주므로 주입 위험이 없다.
 */
/**
 * ⚠ `exec` 루프를 쓰지 않는다 — 전역 정규식은 `lastIndex` 를 들고 다니고, 모듈 수준
 * 가변 상태를 렌더 중에 건드리면 같은 문자열이 호출 순서에 따라 다르게 쪼개진다.
 * `split` 은 상태를 남기지 않는다: 잡은 그룹이 **홀수 자리**에 들어온다.
 */
const MARK = /\*\*([\s\S]+?)\*\*/;

export default function Emphasis({ text }) {
  if (typeof text !== 'string' || !text) return null;
  if (!text.includes('**')) return text;

  return text.split(new RegExp(MARK.source, 'g')).map((chunk, index) => {
    if (!chunk) return null;
    const key = `${index}-${chunk}`;
    return index % 2 === 1 ? <strong key={key}>{chunk}</strong> : <span key={key}>{chunk}</span>;
  });
}
