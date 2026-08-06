import { fireEvent, render, screen, waitFor } from '@testing-library/react';
import { MemoryRouter, Route, Routes } from 'react-router-dom';
import { describe, expect, it, vi } from 'vitest';

import { ApiClientProvider } from '../../shared/api/ApiClientProvider.jsx';
import { ApiError } from '../../shared/api/apiError.js';
import { ProjectProvider } from '../projects/ProjectContext.jsx';
import ServicePolicyContext from '../service-policy/servicePolicyContext.js';
import { CANONICAL_SECTION_ORDER } from '../structured-plan/model/structuredPlanViewModel.js';
import { DocumentUploadPage, StructuredPlanPage } from './DocumentPages.jsx';

function renderPage(element, client, path) {
  const projectId = path.split('/')[2];
  const servicePolicy = {
    loading: false,
    policy: {
      registrationEnabled: true,
      documentProcessingEnabled: true,
      maintenanceMode: false,
      clusterPersonaEnabled: true,
    },
    error: null,
    refresh: vi.fn(async () => undefined),
  };
  return render(
    <MemoryRouter initialEntries={[path]}>
      <ApiClientProvider client={client}>
        <ServicePolicyContext.Provider value={servicePolicy}>
          <ProjectProvider projectId={projectId}>
            <Routes>
              <Route path="/projects/:projectId/documents" element={element} />
              <Route path="/projects/:projectId/structure" element={element} />
            </Routes>
          </ProjectProvider>
        </ServicePolicyContext.Provider>
      </ApiClientProvider>
    </MemoryRouter>,
  );
}

describe('document pages', () => {
  it('validates, selects, removes, and uploads one DOCX file', async () => {
    const client = {
      get: vi.fn(async () => ({ data: [] })),
      upload: vi.fn(async () => ({
        data: { projectId: 1, documentId: 2, versionId: 3, jobId: 4, status: 'QUEUED' },
      })),
    };
    renderPage(<DocumentUploadPage />, client, '/projects/1/documents');
    const input = screen.getByLabelText('사업계획서 파일');

    fireEvent.change(input, {
      target: { files: [new File(['bad'], 'plan.pdf', { type: 'application/pdf' })] },
    });
    expect(await screen.findByRole('alert')).toHaveTextContent('DOCX');

    const docx = new File(['docx'], '긴 사업계획서 이름.docx', {
      type: 'application/vnd.openxmlformats-officedocument.wordprocessingml.document',
    });
    fireEvent.change(input, { target: { files: [docx] } });
    expect(screen.getByText('긴 사업계획서 이름.docx')).toBeInTheDocument();
    fireEvent.click(screen.getByRole('button', { name: '업로드하고 분석 시작' }));

    await waitFor(() => expect(client.upload).toHaveBeenCalledTimes(1));
    expect(client.upload.mock.calls[0][2].headers['Idempotency-Key']).toBeTruthy();
  });

  it('reuses a key for network retry and rotates it when the file changes', async () => {
    const client = {
      get: vi.fn(async () => ({ data: [] })),
      upload: vi.fn(async () => {
        throw new ApiError({ code: 'NETWORK_ERROR', retryable: true });
      }),
    };
    renderPage(<DocumentUploadPage />, client, '/projects/1/documents');
    const input = screen.getByLabelText('사업계획서 파일');
    const first = new File(['one'], 'one.docx', {
      type: 'application/vnd.openxmlformats-officedocument.wordprocessingml.document',
    });
    fireEvent.change(input, { target: { files: [first] } });
    fireEvent.click(screen.getByRole('button', { name: '업로드하고 분석 시작' }));
    await waitFor(() => expect(client.upload).toHaveBeenCalledTimes(1));
    fireEvent.click(screen.getByRole('button', { name: '업로드하고 분석 시작' }));
    await waitFor(() => expect(client.upload).toHaveBeenCalledTimes(2));

    const firstKey = client.upload.mock.calls[0][2].headers['Idempotency-Key'];
    expect(client.upload.mock.calls[1][2].headers['Idempotency-Key']).toBe(firstKey);

    const second = new File(['two'], 'two.docx', {
      type: 'application/vnd.openxmlformats-officedocument.wordprocessingml.document',
    });
    fireEvent.change(input, { target: { files: [second] } });
    fireEvent.click(screen.getByRole('button', { name: '업로드하고 분석 시작' }));
    await waitFor(() => expect(client.upload).toHaveBeenCalledTimes(3));
    expect(client.upload.mock.calls[2][2].headers['Idempotency-Key']).not.toBe(firstKey);
  });

  it('renders 12 real-result sections, evidence, missing summary, and mock disclosure', async () => {
    const sections = CANONICAL_SECTION_ORDER.map((sectionCode, index) => ({
      sectionCode,
      displayName: sectionCode,
      sequence: index + 1,
      status: index === 0 ? 'PRESENT' : 'MISSING',
      extractedContent: index === 0 ? '실제 추출 내용' : null,
      reason: index === 0 ? '문서에서 확인됨' : '정보 부족',
      confidence: index === 0 ? 0.9 : null,
      evidence: index === 0 ? ['근거 문장'] : [],
      sourceBlockReferences: index === 0 ? [1] : [],
    }));
    const client = {
      get: vi.fn(async (path) => {
        if (path.includes('/jobs/latest')) {
          return { data: { jobId: 8, status: 'PARTIAL', progress: 100 } };
        }
        if (path.includes('/structured-plans/latest')) {
          return {
            data: {
              planId: 9,
              provider: 'mock',
              completionRate: 8,
              sections,
              missingFields: [{
                fieldId: 10,
                label: '시장 규모',
                status: 'OPEN',
              }],
            },
          };
        }
        if (path === '/projects/1') {
          return {
            data: {
              id: 1,
              title: '테스트 프로젝트',
              stage: 'STRUCTURING',
              status: 'ACTIVE',
              createdAt: '2026-07-24T00:00:00Z',
            },
          };
        }
        throw new Error(`unexpected path ${path}`);
      }),
    };

    renderPage(<StructuredPlanPage />, client, '/projects/1/structure');

    expect(await screen.findByText('데모 분석 결과')).toBeInTheDocument();
    expect(screen.getByText('12개 사업계획 항목')).toBeInTheDocument();
    expect(screen.getAllByText(/보완 필요/).length).toBeGreaterThan(0);
    expect(screen.getByText('보완이 필요한 필수 항목 1개')).toBeInTheDocument();
    expect(screen.getAllByRole('group', { name: '분석 결과 상태 필터' })).toHaveLength(1);
    expect(document.querySelectorAll('.section-result')).toHaveLength(12);

    fireEvent.click(screen.getByText('1. 사업 개요'));
    expect(screen.getByText('실제 추출 내용')).toBeInTheDocument();
    expect(screen.getByText('근거 문장')).toBeInTheDocument();
  });
});
