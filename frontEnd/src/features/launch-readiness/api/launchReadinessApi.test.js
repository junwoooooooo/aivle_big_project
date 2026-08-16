import { describe, expect, it, vi } from 'vitest';
import { createLaunchReadinessApi } from './launchReadinessApi.js';

describe('launch readiness api', () => {
  it('DOCX를 multipart와 idempotency key로 비동기 분석에 제출한다', async () => {
    const client = { upload: vi.fn(async () => ({ data: { status: 'QUEUED' } })) };
    const api = createLaunchReadinessApi(client);
    const file = new File(['docx'], 'technology.docx');
    await api.startProfessional('7', 'technology', file);
    const [path, form, options] = client.upload.mock.calls[0];
    expect(path).toBe('/api/v3/projects/7/launch-readiness/technology/analysis-runs');
    expect(form.get('file')).toBe(file);
    expect(options.headers['Idempotency-Key']).toBeTruthy();
  });

  it('완료 보고서 조합을 중복 없는 modules query로 요청한다', async () => {
    const client = { download: vi.fn(async () => ({ blob: new Blob(['pdf']) })) };
    const api = createLaunchReadinessApi(client);
    await api.downloadReports('7', ['technology', 'finance']);
    expect(client.download.mock.calls[0][0]).toBe('/api/v3/projects/7/reports/download?modules=technology&modules=finance');
  });

  it('템플릿과 PDF는 JSON get이 아니라 인증된 binary download를 사용한다', async () => {
    const blob = new Blob(['binary']);
    const client = { download: vi.fn(async () => ({ blob })) };
    const api = createLaunchReadinessApi(client);
    await expect(api.professionalTemplate('7', 'technology')).resolves.toBe(blob);
    await expect(api.downloadProfessionalReport('7', 'operations')).resolves.toBe(blob);
    await expect(api.financeTemplate('7')).resolves.toBe(blob);
    await expect(api.downloadFinanceReport('7')).resolves.toBe(blob);
    expect(client.download).toHaveBeenCalledTimes(4);
  });

  it('명시적 PDF 다운로드만 인증 binary endpoint를 호출한다', async () => {
    const client = { download: vi.fn(async () => ({ blob: new Blob(['pdf']) })) };
    const api = createLaunchReadinessApi(client);
    await api.downloadProfessionalReport('7', 'technology');
    await api.downloadFinanceReport('7');
    await api.downloadReports('7', ['technology', 'finance']);
    expect(client.download).toHaveBeenCalledTimes(3);
  });
});
