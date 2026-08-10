import { act, renderHook, waitFor } from '@testing-library/react';
import { beforeEach, describe, expect, it, vi } from 'vitest';

import { useApiClient } from '../../../shared/api/ApiClientProvider.jsx';
import { useJobEvents } from '../../../shared/async-events/index.js';
import { IDEA_INTAKE_SCREEN_STATE } from '../model/ideaIntakeModel.js';
import useIdeaIntake, { IDEA_FAILURE_KIND } from './useIdeaIntake.js';

vi.mock('../../../shared/api/ApiClientProvider.jsx', () => ({ useApiClient: vi.fn() }));
vi.mock('../../../shared/async-events/index.js', () => ({ useJobEvents: vi.fn() }));

describe('useIdeaIntake async recovery', () => {
  beforeEach(() => vi.clearAllMocks());

  it('restores an active job and re-queries the brief after a terminal replay event', async () => {
    let terminal = false;
    useJobEvents.mockImplementation(() => ({ terminal, events: [] }));
    const client = {
      get: vi.fn()
        .mockResolvedValueOnce({ data: response('DERIVING', 'job-1', []) })
        .mockResolvedValueOnce({ data: response('NEEDS_INPUT', null, [{
          questionId: 'q-1', targetFieldKey: 'problem', type: 'FREE_TEXT',
          prompt: '문제를 더 설명해 주세요.', optionsJson: '[]', answered: false, answerJson: null,
        }]) }),
      post: vi.fn(), patch: vi.fn(),
    };
    useApiClient.mockReturnValue(client);
    const { result, rerender } = renderHook(() => useIdeaIntake('42'));

    await waitFor(() => expect(result.current.screenState).toBe(IDEA_INTAKE_SCREEN_STATE.RUNNING));
    expect(result.current.activeJobId).toBe('job-1');

    terminal = true;
    rerender();
    await waitFor(() => expect(result.current.screenState).toBe(IDEA_INTAKE_SCREEN_STATE.NEEDS_QUESTIONS));
    expect(client.get).toHaveBeenCalledTimes(2);
    expect(result.current.questions[0].id).toBe('q-1');
  });

  it('sends the reviewable interpretation and does not confirm before readiness is true', async () => {
    useJobEvents.mockReturnValue({ terminal: false, events: [] });
    const review = {
      ...response('READY_FOR_REVIEW', null, []),
      overview: 'overview',
      fields: [
        { fieldKey: 'ideaOverview', value: 'overview', provenance: 'USER_INPUT', decisionState: 'LOCKED' },
        { fieldKey: 'problem', value: 'problem', provenance: 'USER_INPUT', decisionState: 'LOCKED' },
        { fieldKey: 'targetUsers', value: 'users', provenance: 'USER_INPUT', decisionState: 'LOCKED' },
      ],
      interpretation: interpretation(),
      readiness: { readyForConfirm: false, unansweredQuestionCount: 0 },
      clarificationRound: 2,
      maxClarificationRounds: 2,
    };
    const client = {
      get: vi.fn().mockResolvedValue({ data: review }),
      patch: vi.fn().mockResolvedValue({ data: review }),
      post: vi.fn(),
    };
    useApiClient.mockReturnValue(client);
    const { result } = renderHook(() => useIdeaIntake('42'));
    await waitFor(() => expect(result.current.screenState).toBe(IDEA_INTAKE_SCREEN_STATE.REVIEW));

    await act(async () => result.current.confirmBrief({ preventDefault: vi.fn() }));

    expect(client.patch).toHaveBeenCalledWith(expect.stringContaining('/interpretation'), interpretation(), expect.any(Object));
    expect(client.post).not.toHaveBeenCalled();
  });

  it('stops at RUNNING after commitment canonicalization and waits for the fresh synthesis', async () => {
    useJobEvents.mockReturnValue({ terminal: false, events: [] });
    const review = {
      ...response('READY_FOR_REVIEW', null, []),
      interpretation: {
        ...interpretation(),
        commitmentCandidates: [{ fieldKey: 'price', value: '월 9,900원', evidenceQuote: '월 9,900원으로',
          source: 'AI_DERIVED', origin: 'USER_TEXT', authority: 'REVIEWABLE' }],
      },
      readiness: { readyForConfirm: true, missingFieldKeys: [] },
    };
    const deriving = { ...review, status: 'DERIVING', activeJobId: 'job-reassess', assessmentCurrent: false };
    const client = {
      get: vi.fn().mockResolvedValue({ data: review }),
      patch: vi.fn().mockResolvedValue({ data: deriving }),
      post: vi.fn(),
    };
    useApiClient.mockReturnValue(client);
    const { result } = renderHook(() => useIdeaIntake('42'));
    await waitFor(() => expect(result.current.screenState).toBe(IDEA_INTAKE_SCREEN_STATE.REVIEW));

    await act(async () => result.current.confirmBrief({ preventDefault: vi.fn() }));

    expect(client.patch).toHaveBeenCalledTimes(1);
    expect(client.patch.mock.calls[0][0]).toContain('/commitments');
    expect(client.patch.mock.calls[0][0]).not.toContain('/interpretation');
    expect(client.post).not.toHaveBeenCalled();
    expect(result.current.screenState).toBe(IDEA_INTAKE_SCREEN_STATE.RUNNING);
    expect(result.current.activeJobId).toBe('job-reassess');
  });

  it('routes zero-question missing fields to PATCH and starts final synthesis', async () => {
    useJobEvents.mockReturnValue({ terminal: false, events: [] });
    const needsFields = {
      ...response('NEEDS_INPUT', null, []),
      readiness: { readyForConfirm: false, missingFieldKeys: ['problem'] },
      fieldCatalog: catalog(),
    };
    const deriving = { ...needsFields, status: 'DERIVING', activeJobId: 'job-final' };
    const client = {
      get: vi.fn().mockResolvedValue({ data: needsFields }),
      patch: vi.fn().mockResolvedValue({ data: deriving }),
      post: vi.fn(),
    };
    useApiClient.mockReturnValue(client);
    const { result } = renderHook(() => useIdeaIntake('42'));
    await waitFor(() => expect(result.current.screenState).toBe(IDEA_INTAKE_SCREEN_STATE.NEEDS_FIELDS));

    act(() => result.current.updateBriefField('problem', '해결할 문제'));
    await act(async () => result.current.submitMissingFields({ preventDefault: vi.fn() }));

    expect(client.patch).toHaveBeenCalledWith(expect.stringContaining('/fields'), {
      fields: [expect.objectContaining({ fieldKey: 'problem', value: '해결할 문제' })],
    }, expect.any(Object));
    expect(client.post).not.toHaveBeenCalled();
    expect(result.current.screenState).toBe(IDEA_INTAKE_SCREEN_STATE.RUNNING);
    expect(result.current.activeJobId).toBe('job-final');
  });

  it('routes needs-input without questions or missing fields to review', async () => {
    useJobEvents.mockReturnValue({ terminal: false, events: [] });
    const client = {
      get: vi.fn().mockResolvedValue({ data: response('NEEDS_INPUT', null, []) }),
      patch: vi.fn(), post: vi.fn(),
    };
    useApiClient.mockReturnValue(client);
    const { result } = renderHook(() => useIdeaIntake('42'));
    await waitFor(() => expect(result.current.screenState).toBe(IDEA_INTAKE_SCREEN_STATE.REVIEW));
    expect(client.post).not.toHaveBeenCalled();
  });

  it('routes needs-input with contradictions but no actionable inputs to review', async () => {
    useJobEvents.mockReturnValue({ terminal: false, events: [] });
    const payload = {
      ...response('NEEDS_INPUT', null, []),
      contradictions: [{ fieldKeys: ['problem', 'targetUsers'], summary: 'conflict' }],
    };
    const client = { get: vi.fn().mockResolvedValue({ data: payload }), patch: vi.fn(), post: vi.fn() };
    useApiClient.mockReturnValue(client);
    const { result } = renderHook(() => useIdeaIntake('42'));

    await waitFor(() => expect(result.current.screenState).toBe(IDEA_INTAKE_SCREEN_STATE.REVIEW));
  });

  it('gives explicit execution recovery precedence over business screen state', async () => {
    useJobEvents.mockReturnValue({ terminal: false, events: [] });
    const payload = { ...response('NEEDS_INPUT', null, []), recoveryRequired: true };
    const client = { get: vi.fn().mockResolvedValue({ data: payload }), patch: vi.fn(), post: vi.fn() };
    useApiClient.mockReturnValue(client);
    const { result } = renderHook(() => useIdeaIntake('42'));

    await waitFor(() => expect(result.current.screenState).toBe(IDEA_INTAKE_SCREEN_STATE.RECOVERY));
  });

  it('uses the reanalyze endpoint for recovery and adopts the new job id', async () => {
    useJobEvents.mockReturnValue({ terminal: false, events: [] });
    const client = {
      get: vi.fn().mockResolvedValue({ data: { ...response('NEEDS_INPUT', null, []), recoveryRequired: true } }),
      patch: vi.fn(),
      post: vi.fn().mockResolvedValue({ data: response('DERIVING', 'job-recovery', []) }),
    };
    useApiClient.mockReturnValue(client);
    const { result } = renderHook(() => useIdeaIntake('42'));
    await waitFor(() => expect(result.current.screenState).toBe(IDEA_INTAKE_SCREEN_STATE.RECOVERY));

    await act(async () => result.current.reanalyze());

    expect(client.post.mock.calls[0][0]).toContain('/reanalyze');
    expect(result.current.activeJobId).toBe('job-recovery');
    expect(result.current.screenState).toBe(IDEA_INTAKE_SCREEN_STATE.RUNNING);
  });

  it('reanalyzes a derivation failure instead of refresh-looping the failed state', async () => {
    useJobEvents.mockReturnValue({ terminal: false, events: [] });
    const client = {
      get: vi.fn().mockResolvedValue({ data: response('FAILED', null, []) }),
      patch: vi.fn(),
      post: vi.fn().mockResolvedValue({ data: response('DERIVING', 'job-after-failure', []) }),
    };
    useApiClient.mockReturnValue(client);
    const { result } = renderHook(() => useIdeaIntake('42'));
    await waitFor(() => expect(result.current.screenState).toBe(IDEA_INTAKE_SCREEN_STATE.FAILED));
    expect(result.current.failureKind).toBe(IDEA_FAILURE_KIND.DERIVATION_FAILURE);

    await act(async () => result.current.reanalyze());

    expect(client.get).toHaveBeenCalledTimes(1);
    expect(client.post.mock.calls[0][0]).toContain('/reanalyze');
    expect(result.current.activeJobId).toBe('job-after-failure');
  });

  it('moves a terminal job still attached to DERIVING into recovery and resets on a new job', async () => {
    let terminal = false;
    const observedJobIds = [];
    useJobEvents.mockImplementation((jobId) => {
      observedJobIds.push(jobId);
      return { terminal, events: jobId ? [{ jobId }] : [] };
    });
    const stuck = response('DERIVING', 'job-old', []);
    const recovered = response('DERIVING', 'job-new', []);
    const client = {
      get: vi.fn().mockResolvedValue({ data: stuck }),
      patch: vi.fn(),
      post: vi.fn().mockResolvedValue({ data: recovered }),
    };
    useApiClient.mockReturnValue(client);
    const { result, rerender } = renderHook(() => useIdeaIntake('42'));
    await waitFor(() => expect(result.current.activeJobId).toBe('job-old'));

    terminal = true;
    rerender();
    await waitFor(() => expect(result.current.screenState).toBe(IDEA_INTAKE_SCREEN_STATE.RECOVERY));
    expect(result.current.failureKind).toBe(IDEA_FAILURE_KIND.STATE_RECONCILIATION_REQUIRED);

    terminal = false;
    await act(async () => result.current.reanalyze());

    expect(result.current.activeJobId).toBe('job-new');
    expect(result.current.screenState).toBe(IDEA_INTAKE_SCREEN_STATE.RUNNING);
    expect(result.current.jobEvents.events).toEqual([{ jobId: 'job-new' }]);
    expect(observedJobIds).toContain('job-old');
    expect(observedJobIds).toContain('job-new');
  });
});

function response(status, activeJobId, questions) {
  return {
    briefId: 'brief-1', status, activeJobId, confirmedSnapshotId: null,
    fields: [], questions, readiness: { readyForConfirm: false, missingFieldKeys: [] }, updatedAt: '2026-08-06T12:00:00',
  };
}

function catalog() {
  return [
    { key: 'ideaOverview', label: '아이디어 개요', requiredForConcept: true,
      regulatorySensitive: false, defaultDecisionState: 'LOCKED', allowedQuestionTypes: ['FREE_TEXT'] },
    { key: 'problem', label: '해결하려는 문제', requiredForConcept: true,
      regulatorySensitive: false, defaultDecisionState: 'LOCKED', allowedQuestionTypes: ['FREE_TEXT'] },
    { key: 'targetUsers', label: '예상 사용자', requiredForConcept: true,
      regulatorySensitive: false, defaultDecisionState: 'LOCKED', allowedQuestionTypes: ['FREE_TEXT'] },
  ];
}

function interpretation() {
  return {
    interpretedProblem: '문제 해석', interpretedTargetUsers: '사용자 해석', usageContext: '사용 맥락',
    industryCategory: '업종', researchScope: '조사 범위', conciseIdeaDefinition: '한 줄 정의',
    targetRegionInterpretation: '', relevantKnownCompetitorContext: '',
  };
}
