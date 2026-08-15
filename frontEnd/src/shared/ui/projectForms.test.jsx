import { fireEvent, render, screen } from '@testing-library/react';
import { describe, expect, it, vi } from 'vitest';

import { FileDropzone, ProjectFormRow } from './projectForms.jsx';

describe('프로젝트 공통 폼 시스템', () => {
  it('레이블·설명·오류를 입력 컨트롤과 연결한다', () => {
    render(<div className="project-form-layout">
      <ProjectFormRow id="project-name" label="프로젝트 이름" description="사용자가 알아보기 쉬운 이름" error="이름을 입력해 주세요" required>
        {(props) => <input {...props} />}
      </ProjectFormRow>
    </div>);

    const input = screen.getByLabelText(/프로젝트 이름/);
    expect(input).toHaveAttribute('aria-invalid', 'true');
    expect(input).toHaveAccessibleDescription('사용자가 알아보기 쉬운 이름 이름을 입력해 주세요');
  });

  it('선택·드롭·제거와 업로드 중 상태를 일관되게 제공한다', () => {
    const onFilesChange = vi.fn();
    const file = new File(['sample'], 'sample.pdf', { type: 'application/pdf' });
    const { container, rerender } = render(<FileDropzone files={[]} onFilesChange={onFilesChange} multiple />);
    const input = container.querySelector('input[type="file"]');

    fireEvent.change(input, { target: { files: [file] } });
    expect(onFilesChange).toHaveBeenLastCalledWith([file]);
    fireEvent.drop(input.closest('label'), { dataTransfer: { files: [file] } });
    expect(onFilesChange).toHaveBeenLastCalledWith([file]);

    rerender(<FileDropzone files={[file]} onFilesChange={onFilesChange} uploading />);
    expect(container.querySelector('.project-file-dropzone')).toHaveAttribute('aria-busy', 'true');
    expect(container.querySelector('input[type="file"]')).toBeDisabled();
    expect(screen.getByText('파일을 업로드하고 있습니다')).toBeInTheDocument();
    expect(screen.getByRole('button', { name: 'sample.pdf 제거' })).toBeDisabled();
  });
});
