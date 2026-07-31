import { act, renderHook } from '@testing-library/react';
import { beforeEach, describe, expect, it } from 'vitest';
import { useLegalChecklist } from './useLegalChecklist.js';

describe('useLegalChecklist — 사이클 단위 저장', () => {
  beforeEach(() => localStorage.clear());

  it('cycleId가 있으면 사이클 키에 저장해 버전이 올라도 진행이 유지된다', () => {
    const first = renderHook(() => useLegalChecklist(1, 1, 7));
    act(() => first.result.current.toggle('통신판매업 신고'));
    expect(JSON.parse(localStorage.getItem('legal-checklist:1:cycle:7')))
      .toEqual(['통신판매업 신고']);

    // 재검토로 versionNumber가 2가 되어도 같은 사이클이면 유지
    const second = renderHook(() => useLegalChecklist(1, 2, 7));
    expect(second.result.current.isChecked('통신판매업 신고')).toBe(true);
    expect(second.result.current.checkedActions).toEqual(['통신판매업 신고']);
  });

  it('사이클 키가 비어 있으면 기존 버전 키에서 1회 이어받는다', () => {
    localStorage.setItem('legal-checklist:1:v1', JSON.stringify(['개인정보 처리방침 수립']));
    const { result } = renderHook(() => useLegalChecklist(1, 1, 7));
    expect(result.current.isChecked('개인정보 처리방침 수립')).toBe(true);
  });

  it('cycleId가 없으면 기존 버전 키 동작을 유지한다(하위호환)', () => {
    const { result } = renderHook(() => useLegalChecklist(1, 1, null));
    act(() => result.current.toggle('청약철회·환불 규정 정비'));
    expect(JSON.parse(localStorage.getItem('legal-checklist:1:v1')))
      .toEqual(['청약철회·환불 규정 정비']);
  });
});
