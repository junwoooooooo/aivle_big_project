import { fireEvent, render, screen, waitFor } from '@testing-library/react';
import { MemoryRouter, Outlet, Route, Routes } from 'react-router-dom';
import { describe, expect, it, vi } from 'vitest';

import { IDEA_INTAKE_SCREEN_STATE } from '../model/ideaIntakeModel.js';
import useIdeaIntake from '../hooks/useIdeaIntake.js';
import IdeaIntakePage from './IdeaIntakePage.jsx';

vi.mock('../hooks/useIdeaIntake.js', () => ({ default: vi.fn(), IDEA_FAILURE_KIND: {} }));

describe('Idea confirmation journey', () => {
  it('refreshes module status and offers an explicit proposal CTA without auto navigation', async () => {
    const retry = vi.fn();
    const editConfirmed = vi.fn();
    useIdeaIntake.mockReturnValue({ screenState: IDEA_INTAKE_SCREEN_STATE.CONFIRMED, editConfirmed,
      draft: { intake: {}, fields: {
        ideaOverview: { value: '지역 상점을 위한 예약 서비스' }, problem: { value: '전화 예약의 불편' },
        targetUsers: { value: '소상공인과 고객' }, channels: { value: '모바일' },
      }, interpretation: {
        interpretedProblem: '예약 누락을 줄이는 문제', interpretedTargetUsers: '지역 상점 운영자',
        usageContext: '영업 중 예약 관리', industryCategory: '예약 서비스', researchScope: '지역 사업안',
        conciseIdeaDefinition: '지역 상점 예약 도구', targetRegionInterpretation: '국내',
        relevantKnownCompetitorContext: '기존 예약 플랫폼',
      }, safetyReview: { userFacingReason: '안전 확인을 통과했습니다.', restrictions: ['과장 표현 금지'] } } });
    render(<MemoryRouter initialEntries={['/app/projects/41/idea']}><Routes>
      <Route element={<Outlet context={{ moduleState: { retry }, modules: [{ id: 'concepts', status: 'COMPLETED' }] }} />}>
        <Route path="/app/projects/:projectId/idea" element={<IdeaIntakePage />} />
      </Route>
    </Routes></MemoryRouter>);
    expect(screen.getByRole('heading', { name: '사업 아이디어의 출발점을 알려주세요' })).toBeInTheDocument();
    expect(screen.getByText('지역 상점을 위한 예약 서비스')).toBeInTheDocument();
    expect(screen.getByText('예약 누락을 줄이는 문제')).toBeInTheDocument();
    expect(screen.getByText('안전 확인을 통과했습니다.')).toBeInTheDocument();
    expect(screen.getByText('아이디어 정리가 완료되었습니다.')).toBeInTheDocument();
    const link = screen.getByRole('link', { name: '사업안 생성 및 검토로 이동 →' });
    expect(link).toHaveAttribute('href', '/app/projects/41/concepts');
    expect(window.location.pathname).not.toBe('/app/projects/41/concepts');
    await waitFor(() => expect(retry).toHaveBeenCalledTimes(1));
    vi.spyOn(window, 'confirm').mockReturnValueOnce(true);
    fireEvent.click(screen.getByRole('button', { name: '아이디어 수정' }));
    expect(window.confirm).toHaveBeenCalledWith(expect.stringContaining('기존 사업안과 분석 결과'));
    expect(editConfirmed).toHaveBeenCalledTimes(1);
  });

  it('아이디어 정리 실행 화면으로 전환하면 stage top에서 시작한다', async () => {
    const scrollTo = vi.spyOn(window, 'scrollTo').mockImplementation(() => {});
    useIdeaIntake.mockReturnValue({
      screenState: IDEA_INTAKE_SCREEN_STATE.RUNNING, draft: {}, jobEvents: { events: [] },
      isReanalyzing: false, activeJobId: 'job-resubmit',
    });
    render(<MemoryRouter initialEntries={['/app/projects/41/idea']}><Routes>
      <Route element={<Outlet context={{ modules: [] }} />}>
        <Route path="/app/projects/:projectId/idea" element={<IdeaIntakePage />} />
      </Route>
    </Routes></MemoryRouter>);
    await waitFor(() => expect(scrollTo).toHaveBeenCalledWith(expect.objectContaining({ top: 0 })));
    expect(screen.getByText('아이디어를 정리하고 있습니다')).toBeInTheDocument();
    scrollTo.mockRestore();
  });
});
