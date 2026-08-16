import { describe, expect, it } from 'vitest';

import { legalPresentationKey, legalReportSummaryCounts, uniqueLegalItems } from './legalReportPresentation.js';

describe('법률 결과 presentation 중복 제거', () => {
  it('끝 문장부호와 제한된 의무 종결형만 제거한다', () => {
    expect(legalPresentationKey('신선한 재료 공급업체와의 계약이 필요함.')).toBe('신선한 재료 공급업체와의 계약');
    expect(legalPresentationKey('개인정보 처리방침을 고지해야 합니다.')).toBe('개인정보 처리방침을 고지');
    expect(uniqueLegalItems('신선한 재료 공급업체와의 계약', '신선한 재료 공급업체와의 계약이 필요함.')).toHaveLength(1);
  });

  it('내용이 다른 법률 요구는 합치지 않는다', () => {
    expect(uniqueLegalItems('개인정보 수집 동의', '개인정보 제3자 제공 동의')).toHaveLength(2);
  });

  it('화면과 PDF가 공유하는 요약 건수에도 semantic dedupe를 적용한다', () => {
    expect(legalReportSummaryCounts({
      partnerRequirements: ['공급업체와의 계약'],
      qualificationRequirements: ['공급업체와의 계약이 필요함.'],
    }).partners).toBe(1);
  });
});
