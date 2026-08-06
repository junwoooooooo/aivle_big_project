import { fireEvent, render, screen, waitFor, within } from '@testing-library/react';
import { MemoryRouter, Route, Routes } from 'react-router-dom';
import { useState } from 'react';
import { describe, expect, it, vi } from 'vitest';

import { ApiClientProvider } from '../../shared/api/ApiClientProvider.jsx';
import { ApiError } from '../../shared/api/apiError.js';
import { ProjectProvider } from '../projects/ProjectContext.jsx';
import { StructuredPlanCompletion } from './StructuredPlanCompletion.jsx';
import { toStructuredPlanViewModel } from './model/structuredPlanViewModel.js';

function rawPlan(overrides = {}) {
  return {
    planId: 9,
    projectId: 1,
    sourceDocumentVersionId: 3,
    versionNumber: 1,
    version: 2,
    status: 'NEEDS_INPUT',
    completionRate: 91,
    sections: [],
    missingFields: [{
      fieldId: 10,
      fieldCode: 'SECTION_MARKET_SIZE',
      sectionCode: 'MARKET_SIZE',
      label: '시장 규모',
      required: true,
      status: 'OPEN',
      reason: '시장 규모 정보가 없습니다.',
      priority: 'HIGH',
      userValue: null,
      version: 0,
    }],
    ...overrides,
  };
}

function project(stage = 'STRUCTURING') {
  return {
    id: 1,
    title: '테스트 프로젝트',
    stage,
    status: 'ACTIVE',
    createdAt: '2026-07-24T00:00:00Z',
  };
}

function Harness({ initialPlan, client }) {
  const [plan, setPlan] = useState(toStructuredPlanViewModel(initialPlan));
  return (
    <MemoryRouter initialEntries={['/projects/1/structure']}>
      <ApiClientProvider client={client}>
        <Routes>
          <Route
            path="/projects/:projectId/structure"
            element={(
              <ProjectProvider projectId="1">
                <StructuredPlanCompletion
                  projectId="1"
                  plan={plan}
                  onPlanChange={setPlan}
                />
              </ProjectProvider>
            )}
          />
        </Routes>
      </ApiClientProvider>
    </MemoryRouter>
  );
}

describe('structured plan completion', () => {
  it('submits FILLED with the field lock version and trusts the refetched plan', async () => {
    const latest = rawPlan({
      version: 3,
      status: 'DRAFT',
      completionRate: 100,
      missingFields: [{
        ...rawPlan().missingFields[0],
        status: 'FILLED',
        userValue: '연간 500억 원 규모',
        version: 1,
      }],
    });
    const client = {
      patch: vi.fn(async () => ({ data: latest.missingFields[0] })),
      get: vi.fn(async (path) => (
        path.includes('/structured-plans/latest')
          ? { data: latest }
          : { data: project() }
      )),
      post: vi.fn(),
    };
    render(<Harness initialPlan={rawPlan()} client={client} />);

    fireEvent.click(screen.getByRole('button', { name: '내용 입력' }));
    fireEvent.change(screen.getByLabelText(/보완 내용/), {
      target: { value: '연간 500억 원 규모' },
    });
    fireEvent.click(screen.getByRole('button', { name: '보완 내용 저장' }));

    await waitFor(() => expect(client.patch).toHaveBeenCalledWith(
      '/projects/1/structured-plans/9/missing-fields/10',
      { status: 'FILLED', value: '연간 500억 원 규모', version: 0 },
      undefined,
    ));
    expect(await screen.findByText('보완 내용을 저장했습니다.')).toBeInTheDocument();
    expect(screen.getByText('서버가 계산한 사업계획 완성도')).toBeInTheDocument();
    expect(screen.getByRole('button', { name: '사업계획 확정' })).toBeEnabled();
  });

  it('requires a waiver reason and submits WAIVED with the field lock version', async () => {
    const latest = rawPlan({
      version: 3,
      status: 'DRAFT',
      completionRate: 100,
      missingFields: [{
        ...rawPlan().missingFields[0],
        status: 'WAIVED',
        reason: '해당 시장에는 적용하지 않습니다.',
        version: 1,
      }],
    });
    const client = {
      patch: vi.fn(async () => ({ data: latest.missingFields[0] })),
      get: vi.fn(async (path) => (
        path.includes('/structured-plans/latest')
          ? { data: latest }
          : { data: project() }
      )),
      post: vi.fn(),
    };
    render(<Harness initialPlan={rawPlan()} client={client} />);

    fireEvent.click(screen.getByRole('button', { name: '이번 단계에서 제외' }));
    const dialog = screen.getByRole('dialog', { name: '보완 항목 제외' });
    fireEvent.click(within(dialog).getByRole('button', { name: '제외 사유 저장' }));
    expect(within(dialog).getByText('이번 단계에서 제외하는 이유를 입력해 주세요.')).toBeInTheDocument();
    fireEvent.change(within(dialog).getByLabelText(/제외 사유/), {
      target: { value: '해당 시장에는 적용하지 않습니다.' },
    });
    fireEvent.click(within(dialog).getByRole('button', { name: '제외 사유 저장' }));

    await waitFor(() => expect(client.patch).toHaveBeenCalledWith(
      '/projects/1/structured-plans/9/missing-fields/10',
      { status: 'WAIVED', reason: '해당 시장에는 적용하지 않습니다.', version: 0 },
      undefined,
    ));
    expect(await screen.findByText('해당 항목을 이번 단계에서 제외했습니다.')).toBeInTheDocument();
  });

  it('preserves the user draft across a 409 and retries with the latest field lock version', async () => {
    const serverPlan = rawPlan({
      version: 3,
      missingFields: [{
        ...rawPlan().missingFields[0],
        status: 'FILLED',
        userValue: '다른 사용자의 값',
        version: 1,
      }],
    });
    const savedPlan = rawPlan({
      version: 4,
      status: 'DRAFT',
      completionRate: 100,
      missingFields: [{
        ...serverPlan.missingFields[0],
        userValue: '내가 보존할 값',
        version: 2,
      }],
    });
    let latest = serverPlan;
    const client = {
      patch: vi.fn()
        .mockRejectedValueOnce(new ApiError({
          status: 409,
          code: 'RESOURCE_VERSION_CONFLICT',
        }))
        .mockImplementationOnce(async () => {
          latest = savedPlan;
          return { data: savedPlan.missingFields[0] };
        }),
      get: vi.fn(async (path) => (
        path.includes('/structured-plans/latest')
          ? { data: latest }
          : { data: project() }
      )),
      post: vi.fn(),
    };
    render(<Harness initialPlan={rawPlan()} client={client} />);

    fireEvent.click(screen.getByRole('button', { name: '내용 입력' }));
    fireEvent.change(screen.getByLabelText(/보완 내용/), {
      target: { value: '내가 보존할 값' },
    });
    fireEvent.click(screen.getByRole('button', { name: '보완 내용 저장' }));

    const conflict = await screen.findByText('다른 변경사항이 먼저 저장되었습니다');
    expect(conflict).toBeInTheDocument();
    expect(screen.getAllByText('다른 사용자의 값')).not.toHaveLength(0);
    expect(screen.getAllByText('내가 보존할 값')).not.toHaveLength(0);
    fireEvent.click(screen.getByRole('button', { name: '최신 버전에 다시 저장' }));

    await waitFor(() => expect(client.patch).toHaveBeenLastCalledWith(
      '/projects/1/structured-plans/9/missing-fields/10',
      { status: 'FILLED', value: '내가 보존할 값', version: 1 },
      undefined,
    ));
    expect(await screen.findByText('보완 내용을 저장했습니다.')).toBeInTheDocument();
  });

  it('confirms once, refreshes project stage, and renders a read-only legal-review CTA', async () => {
    const draft = rawPlan({
      status: 'DRAFT',
      completionRate: 100,
      missingFields: [],
    });
    const confirmed = {
      ...draft,
      version: 3,
      status: 'CONFIRMED',
      confirmedAt: '2026-07-24T03:30:00Z',
      confirmedBy: 7,
    };
    const client = {
      get: vi.fn(async () => ({ data: project('LEGAL_REVIEW') })),
      patch: vi.fn(),
      post: vi.fn(async () => ({ data: confirmed })),
    };
    render(<Harness initialPlan={draft} client={client} />);

    fireEvent.click(screen.getByRole('button', { name: '사업계획 확정' }));
    const dialog = screen.getByRole('dialog', { name: '사업계획을 확정하시겠습니까?' });
    fireEvent.click(within(dialog).getByRole('button', { name: '확인하고 확정' }));

    await waitFor(() => expect(client.post).toHaveBeenCalledWith(
      '/projects/1/structured-plans/9/confirm',
      { version: 2 },
      undefined,
    ));
    expect(await screen.findByText('구조화된 사업계획이 확정되었습니다')).toBeInTheDocument();
    expect(screen.queryByRole('button', { name: '내용 입력' })).not.toBeInTheDocument();
    expect(screen.getByRole('link', { name: '법률·규제 검토 단계로 이동' }))
      .toHaveAttribute('href', '/app/projects/1/legal');
  });
});
