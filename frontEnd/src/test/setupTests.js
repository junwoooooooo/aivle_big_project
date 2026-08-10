import '@testing-library/jest-dom/vitest';

import {
  afterEach,
} from 'vitest';
import {
  cleanup,
} from '@testing-library/react';

afterEach(() => {
  cleanup();
  // vitest 4 + jsdom 29 조합에서 `localStorage` 가 Storage API 를 다 갖추지 않고 오는 경우가 있다.
  // 그러면 teardown 이 TypeError 로 죽어 **테스트 결과가 아니라 정리 단계가 실패**하고,
  // 그 파일의 모든 테스트가 한꺼번에 빨개진다(원인이 안 보인다).
  // 있으면 지우고 없으면 넘어간다 — 정리는 검사가 아니다.
  globalThis.localStorage?.clear?.();
  globalThis.sessionStorage?.clear?.();
});
