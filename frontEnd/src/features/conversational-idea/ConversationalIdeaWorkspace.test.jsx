import { fireEvent, render, screen, waitFor } from '@testing-library/react';
import { MemoryRouter, Route, Routes } from 'react-router-dom';
import { beforeEach, describe, expect, it, vi } from 'vitest';

import { ApiClientProvider } from '../../shared/api/ApiClientProvider.jsx';
import { ConversationalIdeaWorkspace } from './ConversationalIdeaWorkspace.jsx';

const { jobEventsMock } = vi.hoisted(() => ({ jobEventsMock: vi.fn() }));

vi.mock('../../shared/async-events/index.js', () => ({
  useJobEvents: (...args) => jobEventsMock(...args),
  JobTimeline: ({ title }) => <section aria-label={title}>{title}</section>,
}));

function renderWorkspace(client) {
  return render(<MemoryRouter initialEntries={['/app/projects/7/journey/idea']}>
    <ApiClientProvider client={client}><Routes><Route path="/app/projects/:projectId/journey/idea" element={<ConversationalIdeaWorkspace />} /></Routes></ApiClientProvider>
  </MemoryRouter>);
}

function loadedWorkspace() {
  return {
    id: 3, status: 'ACTIVE', domainState: 'NEEDS_INPUT', activeJobId: 'job-3',
    attachments: [{ id: 7, filename: 'source.docx', status: 'EXTRACTED', failureCode: null }],
    messages: [{ id: 1, sequence: 1, role: 'ASSISTANT', type: 'QUESTION_SET', text: '두 가지를 확인할게요.',
      occurredAt: '2026-08-05T10:00:00', contradictions: [], readiness: 'NEEDS_INPUT',
      questions: [{ id: 'q1', fieldKey: 'targetRegion', prompt: '어느 지역인가요?', type: 'FREE_TEXT', options: [], allowUndecided: true }],
      envelope: { schemaVersion: '1.0', messageType: 'QUESTION_SET', payload: {
        text: '두 가지를 확인할게요.', contradictions: [], readiness: 'NEEDS_INPUT',
        questions: [{ id: 'q1', fieldKey: 'targetRegion', prompt: '어느 지역인가요?', type: 'FREE_TEXT', options: [], allowUndecided: true }],
      } } }],
    brief: { id: 2, version: 1, state: 'DRAFT', hash: 'sha256:abc', missingFields: ['targetRegion'],
      fields: [{ fieldKey: 'problem', value: 'food waste', decisionStatus: 'OPEN', sourceType: 'AI_PROPOSED', userConfirmed: false }] },
  };
}

describe('ConversationalIdeaWorkspace', () => {
  beforeEach(() => {
    vi.clearAllMocks();
    jobEventsMock.mockReturnValue({
      events: [], transport: 'SSE', connectionState: 'CONNECTED',
      stop: vi.fn(), reconnect: vi.fn(),
    });
  });

  it('shows the empty conversation while preserving a prominent workspace heading', async () => {
    const client = { get: vi.fn(async () => ({ data: null })) };
    renderWorkspace(client);
    expect(await screen.findByRole('heading', { name: '대화로 사업 기회를 구체화하세요' })).toBeInTheDocument();
    expect(screen.getByText('어떤 문제를 해결하고 싶으신가요?')).toBeInTheDocument();
    expect(screen.getByText('Opportunity Brief 보기', { selector: 'button' })).toHaveAttribute('aria-expanded', 'false');
  });

  it('renders question, draft provenance, missing gate and mobile brief toggle without technical data', async () => {
    const client = { get: vi.fn(async () => ({ data: loadedWorkspace() })) };
    renderWorkspace(client);
    expect(await screen.findByText('어느 지역인가요?')).toBeInTheDocument();
    expect(screen.getByText('source.docx')).toBeInTheDocument();
    expect(screen.getByText('AI/자료 제안')).toBeInTheDocument();
    expect(screen.getByRole('button', { name: 'Brief 전체 확인' })).toBeDisabled();
    expect(screen.queryByText(/technicalCode|providerBody|stack trace/i)).not.toBeInTheDocument();
    fireEvent.click(screen.getByText('Opportunity Brief 보기', { selector: 'button' }));
    expect(screen.getByText('Opportunity Brief 닫기', { selector: 'button' })).toHaveAttribute('aria-expanded', 'true');
  });

  it('edits a field and its decision status through the versioned brief API', async () => {
    const current = loadedWorkspace();
    const client = {
      get: vi.fn(async () => ({ data: current })),
      put: vi.fn(async () => ({ data: { ...current.brief, version: 2,
        fields: [{ ...current.brief.fields[0], value: 'confirmed waste', decisionStatus: 'LOCKED', userConfirmed: true }] } })),
    };
    renderWorkspace(client);
    await screen.findByLabelText('문제 또는 기회');
    fireEvent.change(screen.getByLabelText('문제 또는 기회 결정 상태'), { target: { value: 'LOCKED' } });
    const save = screen.getAllByRole('button', { name: '직접 저장' })[0];
    await waitFor(() => expect(save).not.toBeDisabled());
    fireEvent.click(save);
    await waitFor(() => expect(client.put).toHaveBeenCalledOnce());
    expect(client.put.mock.calls[0][1]).toMatchObject({ value: 'food waste', decisionStatus: 'LOCKED' });
  });

  it('creates a conversation and sends a user message as an asynchronous job', async () => {
    const created = { ...loadedWorkspace(), id: 8, messages: [], brief: null, activeJobId: null, domainState: 'EMPTY' };
    const client = {
      get: vi.fn(async () => ({ data: null })),
      post: vi.fn(async (path) => {
        if (path.endsWith('/idea-conversations')) return { data: created };
        if (path.endsWith('/messages')) return { data: { message: { id: 9, role: 'USER', type: 'TEXT', text: '냉장고 음식 낭비', questions: [], contradictions: [] }, jobId: 'job-9', jobStatus: 'QUEUED' } };
        throw new Error(path);
      }),
    };
    renderWorkspace(client);
    fireEvent.change(await screen.findByPlaceholderText('문제, 고객, 원하는 결과를 자유롭게 적어주세요.'), { target: { value: '냉장고 음식 낭비' } });
    fireEvent.click(screen.getByRole('button', { name: '전송' }));
    await waitFor(() => expect(client.post).toHaveBeenCalledTimes(2));
    expect(client.post.mock.calls[0][1]).toEqual({ importCurrentIdeaSource: true });
    expect(client.post.mock.calls[1][1]).toMatchObject({ text: '냉장고 음식 낭비' });
  });

  it('starts Concept Exploration only from a confirmed Brief and READY Boundary', async () => {
    const current = {
      ...loadedWorkspace(), domainState: 'CONFIRMED', activeJobId: null,
      brief: { ...loadedWorkspace().brief, id: 12, version: 4, state: 'CONFIRMED', hash: 'sha256:brief', missingFields: [] },
    };
    const boundary = {
      stale: false,
      run: { runId: 20, jobId: 'boundary-job', status: 'READY' },
      version: {
        boundaryVersionId: 22, versionNumber: 2, status: 'READY',
        regulatoryBoundaryHash: 'sha256:boundary', rules: [], sourceWarnings: [],
      },
    };
    const started = {
      batchId: 31, jobId: 'concept-job', status: 'QUEUED',
      confirmedBriefVersionId: 12, briefHash: 'sha256:brief',
      regulatoryBoundaryVersionId: 22, boundaryHash: 'sha256:boundary', stale: false,
    };
    const client = {
      get: vi.fn(async (path) => {
        if (path.endsWith('/idea-conversations/current')) return { data: current };
        if (path.endsWith('/regulatory-boundaries/current')) return { data: boundary };
        if (path.endsWith('/concept-explorations/current')) return { data: { batch: null, slots: [], concepts: [] } };
        throw new Error(path);
      }),
      post: vi.fn(async (path, body) => {
        if (path.endsWith('/concept-explorations')) {
          expect(body).toEqual({ confirmedBriefVersionId: 12, regulatoryBoundaryVersionId: 22 });
          return { data: started };
        }
        throw new Error(path);
      }),
    };

    renderWorkspace(client);
    fireEvent.click(await screen.findByRole('button', { name: 'Concept 탐색 시작' }));

    await waitFor(() => expect(client.post).toHaveBeenCalledOnce());
    expect(await screen.findByRole('region', { name: 'Concept Workboard' })).toBeInTheDocument();
    expect(screen.queryByRole('textbox')).not.toBeInTheDocument();
  });

  it('refetches once for a duplicated terminal sequence and renders the Assistant question and Brief', async () => {
    const processing = {
      ...loadedWorkspace(), domainState: 'PROCESSING', brief: null,
      messages: [{ id: 8, sequence: 1, role: 'USER', type: 'TEXT', text: '사업 아이디어',
        occurredAt: '2026-08-05T10:16:57.894Z', questions: [], contradictions: [] }],
      activeJobId: 'job-terminal',
    };
    const completed = {
      ...loadedWorkspace(), activeJobId: null,
      messages: [...processing.messages, {
        id: 9, sequence: 2, role: 'ASSISTANT', type: 'QUESTION_SET', text: '두 가지를 확인할게요.',
        occurredAt: '2026-08-05T10:16:58.000Z', contradictions: [], readiness: 'NEEDS_INPUT',
        questions: [{ id: 'q2', fieldKey: 'targetRegion', prompt: '어느 지역인가요?',
          type: 'FREE_TEXT', options: [], allowUndecided: true }],
        envelope: { schemaVersion: '1.0', messageType: 'QUESTION_SET', payload: {
          text: '두 가지를 확인할게요.', contradictions: [], readiness: 'NEEDS_INPUT',
          questions: [{ id: 'q2', fieldKey: 'targetRegion', prompt: '어느 지역인가요?',
            type: 'FREE_TEXT', options: [], allowUndecided: true }],
        } },
      }],
      brief: { ...loadedWorkspace().brief, hash: 'sha256:refreshed' },
    };
    jobEventsMock.mockImplementation((jobId) => ({
      events: jobId === 'job-terminal' ? [
        { jobId, sequence: 7, status: 'NEEDS_INPUT', messageKey: 'job.idea.questions.completed' },
        { jobId, sequence: 7, status: 'NEEDS_INPUT', messageKey: 'job.idea.questions.completed' },
      ] : [],
      transport: 'SSE', connectionState: 'terminal', stop: vi.fn(), reconnect: vi.fn(),
    }));
    let conversationReads = 0;
    const client = {
      get: vi.fn(async (path) => {
        if (path.endsWith('/idea-conversations/current')) {
          conversationReads += 1;
          return { data: conversationReads === 1 ? processing : completed };
        }
        if (path.endsWith('/regulatory-boundaries/current')) return { data: null };
        if (path.endsWith('/concept-explorations/current')) return { data: { batch: null, slots: [], concepts: [] } };
        throw new Error(path);
      }),
    };

    renderWorkspace(client);

    expect(await screen.findByText('두 가지를 확인할게요.')).toBeInTheDocument();
    expect(screen.getByText('어느 지역인가요?')).toBeInTheDocument();
    expect(screen.getByText('Version 1 · DRAFT')).toBeInTheDocument();
    await waitFor(() => expect(conversationReads).toBe(2));
    expect(screen.queryByText(/technicalCode|providerBody/i)).not.toBeInTheDocument();
  });
});
