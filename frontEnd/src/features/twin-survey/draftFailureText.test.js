import { describe, expect, it } from 'vitest';

import { draftFailureText } from './draftFailureText.js';

/**
 * 실측에서 잡힌 회귀를 못박는다(2026-08-11). 컨셉이 없는 프로젝트에서 초안을 누르면
 * 서버는 404 로 **무엇을 해야 하는지** 말해 주는데, 화면이 그것을 버리고
 * 「잠시 후 다시 시도해 주세요」를 내놨다 — 기다려도 절대 안 풀리는 실패다.
 */
describe('draftFailureText — 재시도로 안 풀리는 실패에 재시도하라고 말하지 않는다', () => {
  it('확정된 컨셉이 없으면 서버가 시킨 일을 그대로 보인다', () => {
    expect(draftFailureText({
      code: 'RESOURCE_NOT_FOUND',
      message: '확정된 컨셉이 없다 — 컨셉을 먼저 고르고 가설을 확정하라',
      retryable: false,
    })).toBe('확정된 컨셉이 없다 — 컨셉을 먼저 고르고 가설을 확정하라');
  });

  it('팔 수 있는 쌍이 0개일 때도 서버 문구를 그대로 보인다', () => {
    expect(draftFailureText({
      code: 'TWIN_STIMULUS_NO_SERVICEABLE_PAIR',
      message: '지금 컨셉으로는 팔 수 있는 비교 쌍을 못 만들었습니다. 차별점을 하나 이상 확정해 주세요.',
      retryable: false,
    })).toContain('차별점을 하나 이상 확정');
  });

  it('진짜 일시적 실패는 재시도 문구를 쓴다', () => {
    expect(draftFailureText({
      code: 'EXTERNAL_AI_SERVICE_UNAVAILABLE',
      message: 'AI 서비스에 일시적으로 연결할 수 없습니다.',
      retryable: true,
    })).toContain('재시도 가능 여부를 확인');
  });

  it('네트워크가 끊긴 경우처럼 서버 문구가 없으면 일반 문구로 떨어진다', () => {
    expect(draftFailureText({ code: 'NETWORK_ERROR', retryable: true }))
      .toBe('네트워크 연결을 확인한 뒤 다시 시도해 주세요.');
  });
});
