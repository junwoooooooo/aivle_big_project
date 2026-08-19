import { describe, expect, it, vi } from 'vitest';
import { cleanup, fireEvent, render, screen } from '@testing-library/react';
import RefinementSummary from './RefinementSummary.jsx';

/**
 * <b>다듬기 화면이 결말을 «무엇이라 부르는가».</b>
 *
 * <p>이 파일이 생기기 전까지 이 화면을 렌더하는 테스트는 <b>0건</b>이었다. 그래서
 * 「법이 막았는데 «고칠 것 없음»이라 말하는」 거짓이 3층 테스트가 전부 초록인 채로
 * 반년을 살아 있었다. 문구는 이 제품이 파는 것 그 자체라 여기서 잠근다.
 */
const 컨셉 = { conceptName: '프리미엄 냉동 간편식 A', price: '1팩 8,900원' };

const 그린다 = (result, over = {}) => render(
  <RefinementSummary
    result={result}
    concept={컨셉}
    evidenceSubjects={new Map()}
    onJumpSubject={() => {}}
    onBack={() => {}}
    {...over}
  />,
);

const 제안 = (over = {}) => ({
  round: 1, field: 'price', title: '가격을 시장 안으로', before: '1팩 8,900원',
  after: '1팩 6,900원', reason: '편의점 도시락이 3,900~6,500원이에요.',
  evidenceIds: ['C-F015'], source: 'MARKET', legalRef: null, accepted: null, ...over,
});

const 근거 = (over = {}) => new Map([['C-F015', {
  id: 'C-F015', quote: '6000원 한상가득 도시락(한식편)', subject: '편의점 도시락',
  period: '2025', metric: '판매가', value: 6000, unit: '원',
  sourceKind: 'aggregate', sourceUrl: 'https://gs25.gsretail.com/event/detail',
  caveats: [], placement: null, ...over,
}]]);

const 결말 = (outcome, over = {}) => ({
  outcome, rounds: 1, changes: [], unresolved: [], deltaLegal: null, narrative: [],
  retry: { failed: false, attempts: 0, maxAttempts: 3 }, ...over,
});

describe('결말 문구', () => {
  it('법이 막았으면 「고칠 것 없음」이라 하지 않는다', () => {
    그린다(결말('LEGAL_BLOCKED', { unresolved: ['식품표시광고법 제8조 — 부당한 표시'] }));
    expect(screen.getByText('법이 막았어요')).toBeTruthy();
    // ★ 이것이 이 테스트의 전부다 — 막힌 사업안을 「컨셉은 그대로예요」로 읽히게 하면
    //   사용자가 안심하고 확정한다.
    expect(screen.queryByText(/시장 근거로 바꿀 것이 나오지 않았어요/)).toBeNull();
  });

  it('진짜 고칠 것이 없을 때만 「고칠 것 없음」이라 한다', () => {
    그린다(결말('NOTHING_TO_FIX'));
    expect(screen.getByText('고칠 것 없음')).toBeTruthy();
    expect(screen.getByText(/시장 근거로 바꿀 것이 나오지 않았어요/)).toBeTruthy();
  });

  it('모르는 결말은 「아직 안 함」으로 떨어진다 — 빈 화면을 그리지 않는다', () => {
    그린다(결말('무엇인가_새로_생긴_값'));
    expect(screen.getByText('아직 안 함')).toBeTruthy();
  });

  it('법률을 다시 본 적 없는 수렴은 「통과했어요」라고 말하지 않는다', () => {
    그린다(결말('CONVERGED'));
    expect(screen.getByText('다듬기 완료')).toBeTruthy();
    expect(screen.queryByText('법률 검토까지 통과했어요.')).toBeNull();
    expect(screen.getByText(/법을 다시 보지는 않았어요/)).toBeTruthy();
  });
});

describe('계산대 — 고른 것만 반영된다', () => {
  const 고를차례 = (over = {}) => 결말('AWAITING_DECISION', { changes: [제안()], ...over });

  it('고르는 중에는 확정 버튼이 뜨지 않는다', () => {
    그린다(고를차례(), { onFinalize: () => {} });
    // 아직 답하지 않은 제안을 두고 「이 컨셉으로 확정」을 누를 수 있으면 안 된다.
    expect(screen.queryByText('이 컨셉으로 확정하기')).toBeNull();
  });

  it('체크한 것만 서버로 간다', () => {
    const onDecide = vi.fn();
    그린다(고를차례(), { onDecide, evidenceById: 근거() });
    fireEvent.click(screen.getByRole('checkbox'));
    fireEvent.click(screen.getByText('체크한 1개만 반영하기'));
    expect(onDecide).toHaveBeenCalledWith(1, ['price']);
  });

  it('하나도 안 골랐으면 반영 버튼이 죽어 있다 — 「전부 넘기기」와 같은 일을 하면 안 된다', () => {
    그린다(고를차례(), { onDecide: () => {} });
    expect(screen.getByText('반영할 것을 고르세요').closest('button').disabled).toBe(true);
  });

  it('이미 답이 끝난 라운드의 제안은 체크할 수 없다', () => {
    그린다(결말('AWAITING_DECISION', {
      rounds: 2,
      changes: [제안({ round: 1, accepted: false }), 제안({ round: 2, field: 'channels' })],
    }), { onDecide: () => {} });
    // 눌러도 아무 일이 없는 체크박스가 생기면 사용자는 반영됐다고 믿는다.
    expect(screen.getAllByRole('checkbox')).toHaveLength(1);
    expect(screen.getByText('넘김')).toBeTruthy();
  });

  it('고를 차례에는 컨셉 원문 문단을 아예 안 낸다', () => {
    // 아직 정해진 것이 없는데 문단은 «지금 컨셉»을 최종본처럼 적는다. 머리글은
    // 「아직 아무것도 바뀌지 않았어요」, 문단은 옛 값, 제안은 새 값 — 세 값이 동시에 말한다.
    const { container } = 그린다(고를차례(), { onDecide: () => {} });
    expect(container.querySelector('.cr-doc')).toBeNull();
  });

  it('고른 것만 오른쪽에 선다 — 안 고른 줄은 「그대로」다', () => {
    // 체크박스만 있으면 「이걸 고르면 내 사업안이 어떻게 되는가」가 어디에도 안 보인다.
    const { container } = 그린다(고를차례(), { onDecide: () => {} });
    expect(container.querySelector('[title="고르면 이렇게 돼요"]')).toBeTruthy();
    expect(screen.getByText('그대로')).toBeTruthy();

    fireEvent.click(screen.getByRole('checkbox'));
    expect(screen.queryByText('그대로')).toBeNull();
    expect(container.querySelector('.cr-cmp__b').textContent).toContain('6,900');
  });

  // ★ **법률 카드를 통째로 뺐다**(2026-08-16 사용자 지시: 「애매하다」). 앞서 이 자리에는
  //   ① 고를 차례에는 안 낸다 ② 볼 법이 없으면 한 줄만 낸다 — 두 검사가 있었다.
  //   카드가 아예 없으니 둘을 하나로 합쳐 **어느 국면에서도 안 선다**를 못 박는다.
  //   ⚠ 되살릴 때 되살릴 것은 카드와 «이 두 국면 구분»이 같이다.
  it('법률 카드는 어느 국면에도 안 선다 — 조항 인용은 화면에서 뺐다', () => {
    그린다(고를차례(), { onDecide: () => {} });
    expect(screen.queryByText('법률 검토')).toBeNull();

    cleanup();
    그린다(결말('CONVERGED', { changes: [제안({ accepted: true })] }));
    expect(screen.queryByText('법률 검토')).toBeNull();
    expect(screen.queryByText('새롭게 추가할 법률 검토가 없어요.')).toBeNull();
  });

  // ⚠⚠ **「법률 자문이 아니에요」를 뺐다**(2026-08-16 사용자 지시 두 번).
  //   규칙과 그 근거를 지우지 않고 여기 남긴다 — 프로젝트 규칙(CLAUDE.md §8)은 이 문장을
  //   「경계 표시」로 분류하고 제거를 금지한다. 이 카드는 법령 이름과 조항 번호를 그대로
  //   인용하는데 이 제품에는 자문 자격이 없다. 되살릴 자리는 조항을 펴기 직전이다.
  it('확정을 마치면 같은 자리가 「다음」으로 바뀐다', () => {
    // 확정 버튼을 그대로 두면 다시 누르게 되고, 서버가 거절해 **성공한 일이 실패로 보인다.**
    그린다(결말('CONVERGED', { changes: [제안({ accepted: true })], finalized: true }),
      { onFinalize: () => {}, onNext: () => {} });
    expect(screen.queryByText('컨셉 확정')).toBeNull();
    expect(screen.getByText('다음 →')).toBeTruthy();
  });

  it('확정 전에는 「컨셉 확정」이 선다', () => {
    그린다(결말('CONVERGED', { changes: [제안({ accepted: true })] }),
      { onFinalize: () => {}, onNext: () => {} });
    expect(screen.getByText('컨셉 확정')).toBeTruthy();
    expect(screen.queryByText('다음 →')).toBeNull();
  });

  it('★ 확정 직전에는 「법률 자문 아님」이 선다 — 여기가 그 경계의 자리다', () => {
    // 경계가 가장 필요한 순간은 조항을 읽을 때가 아니라 **최종 컨셉으로 굳히는** 순간이다.
    // 여기서부터 기술·운영·재무·마케팅이 이 값을 읽는다.
    그린다(결말('CONVERGED', { changes: [제안({ accepted: true })] }));
    expect(screen.getByText(/법률 자문은 아니에요/)).toBeTruthy();
  });

  it('전부 넘겼으면 막다른 길이 아니다 — 다시 받을 문이 보인다', () => {
    그린다(결말('DECLINED', { rounds: 1, changes: [제안({ accepted: false })] }),
      { onRetry: () => {} });
    expect(screen.getByText('다른 제안 받기')).toBeTruthy();
    expect(screen.getByText(/남은 라운드 2번/)).toBeTruthy();
  });

  // ⚠ 판 ㊻ 에서 **상자를 한 줄로 줄였다**(사용자 지시). 문구가 바뀌었을 뿐
  //   「앞 화면 값은 옛 컨셉 기준」과 「확정 먼저」 두 사실은 그대로 말해야 한다.
  it('전부 넘겨서 안 바뀌었으면 「다시 조사하라」고 하지 않는다', () => {
    그린다(결말('DECLINED', { changes: [제안({ accepted: false })] }));
    expect(screen.queryByText(/고치기 전 컨셉 기준/)).toBeNull();
  });

  // ⚠⚠ **판 ㊻ 에서 이 안내를 뺐다** (2026-08-16 사용자 지시 세 번: 상자 → 한 줄 → 뺌).
  //   원래 규칙과 그 근거를 지우지 않고 여기 남긴다 —
  //   ① 반영이 일어나면 `staleDependents()` 가 시드를 낡음으로 만든다. 앞 화면의 판정·
  //      수치·성적표는 **고치기 전 컨셉으로 잰 값** 그대로 남는다.
  //   ② 확정 전에 「다시 조사」를 누르면 `MarketAnalysisSeedLookup.current()` 가 빈손이라
  //      서버가 거절하고 화면에는 「잠시 후 다시 시도해 주세요」만 뜬다(2026-08-15 크롬 실측).
  //   되살릴 자리는 이 자리가 아니라 **확정 버튼 옆**이다.
  it('재조사 안내는 **일부러 없다** — 되살릴 자리는 확정 버튼 옆이다', () => {
    그린다(결말('CONVERGED', { changes: [제안({ accepted: true })] }));
    expect(screen.queryByText(/고치기 전 컨셉 기준/)).toBeNull();
    expect(screen.queryByText(/아직 다시 조사하지 않았어요/)).toBeNull();
  });
});

describe('감사가 잡은 것들', () => {
  it('고를 차례에는 「이렇게 고쳤어요」라고 말하지 않는다', () => {
    // 결정 전에는 적용된 것도 초록도 없다 — 첫 문장이 배지와 정반대면 안 된다.
    그린다(결말('AWAITING_DECISION', { changes: [제안()] }));
    expect(screen.getByText(/아직 아무것도 바뀌지 않았어요/)).toBeTruthy();
    expect(screen.queryByText(/컨셉을 이렇게 고쳤어요/)).toBeNull();
  });

  it('「값이 서지 않았어요」에서는 확정 버튼을 안 낸다 — 누르면 서버가 거절한다', () => {
    그린다(결말('DECISION_NOT_APPLIED', { changes: [제안({ accepted: true })] }),
      { onFinalize: () => {}, onRetry: () => {} });
    expect(screen.queryByText('이 컨셉으로 확정하기')).toBeNull();
    // 대신 다음에 할 것이 보여야 한다 — 아니면 막다른 길이다.
    expect(screen.getByText('다른 제안 받기')).toBeTruthy();
  });

  it('서술문 조각은 «채택분» 번호로 착지한다', () => {
    // 서버는 채택분만 세고 화면은 전량을 그린다. 화면 순번으로 착지시키면
    // **거절한 제안의 이유 칸**으로 간다.
    const { container } = 그린다(결말('CONVERGED', {
      changes: [
        제안({ field: 'price', accepted: false, narrativeRef: null }),
        제안({ field: 'channels', accepted: true, narrativeRef: 1 }),
      ],
      narrative: [{ text: '온라인몰 중심으로', changeRef: 1 }],
    }));
    const 착지 = container.querySelector('#cr-nref-1');
    expect(착지).toBeTruthy();
    // 그 착지점이 «채택된» 제안 쪽에 있어야 한다.
    expect(착지.closest('.cr-why').textContent).toContain('반영함');
  });

  it('출처 종류를 사람 말로 옮긴다 — 영문 토큰이 새면 안 된다', () => {
    그린다(결말('AWAITING_DECISION', { changes: [제안()] }),
      { evidenceById: 근거({ sourceKind: 'community' }) });
    expect(screen.getByText(/블로그·카페/)).toBeTruthy();
    expect(screen.queryByText(/community/)).toBeNull();
  });
});

describe('근거 원문 — 기계가 못 가리는 것을 사람이 읽는다', () => {
  it('인용문·누가 쟀나·언제·무엇을 편다', () => {
    그린다(결말('AWAITING_DECISION', { changes: [제안()] }), { evidenceById: 근거() });
    expect(screen.getByText(/6000원 한상가득 도시락/)).toBeTruthy();
    // 「누가 쟀나」 — issuer 는 실측 200건 중 0건이라 출처 종류와 집 이름을 쓴다.
    expect(screen.getByText(/gs25\.gsretail\.com/)).toBeTruthy();
    expect(screen.getByText(/편의점 도시락 · 2025/)).toBeTruthy();
  });

  it('경계를 그대로 펴 준다', () => {
    그린다(결말('AWAITING_DECISION', { changes: [제안()] }),
      { evidenceById: 근거({ caveats: ['⚠ 우리 세그먼트가 아니라 전체 간편식 기준이다'] }) });
    expect(screen.getByText(/전체 간편식 기준이다/)).toBeTruthy();
  });

  it('조사가 결론에 안 쓴 자료는 그렇다고 말한다', () => {
    // 재료 200건 중 147건이 이것이었다 — 경계 없이 오면 멀쩡한 근거로 보인다.
    그린다(결말('AWAITING_DECISION', { changes: [제안()] }),
      { evidenceById: 근거({ placement: '밖' }) });
    expect(screen.getByText(/조사가 결론에 쓰지 않은 참고 자료예요/)).toBeTruthy();
  });

  it('인용문이 없는 근거는 「원문이 없어요」라고 정직하게 적는다', () => {
    그린다(결말('AWAITING_DECISION', { changes: [제안()] }),
      { evidenceById: 근거({ quote: null }) });
    expect(screen.getByText(/계산으로 만든 값이라 원문이 없어요/)).toBeTruthy();
  });

  it('화면에 없는 근거는 없다고 말한다 — 조용히 비우지 않는다', () => {
    그린다(결말('AWAITING_DECISION', { changes: [제안({ evidenceIds: ['C-F076'] })] }),
      { evidenceById: 근거() });
    expect(screen.getAllByText(/지금 화면의 조사 결과에 없어요/).length).toBeGreaterThan(0);
  });
});
