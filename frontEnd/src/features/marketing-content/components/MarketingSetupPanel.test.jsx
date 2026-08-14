import { fireEvent, render } from '@testing-library/react';
import { describe, expect, it, vi } from 'vitest';

import MarketingSetupPanel from './MarketingSetupPanel.jsx';

const value = {
  referenceImage: null, contentType: 'SOCIAL_POST', channel: '', purpose: '', tone: '',
  length: 'MEDIUM', callToAction: '', requiredPhrases: '', excludedPhrases: '', additionalInstruction: '',
};

describe('MarketingSetupPanel 공통 폼 적용', () => {
  it('가로형 필드 행과 공통 이미지 드롭존을 사용한다', () => {
    const onChange = vi.fn();
    const { container } = render(<MarketingSetupPanel value={value} onChange={onChange} onSubmit={vi.fn()} />);

    expect(container.querySelectorAll('.project-form-layout .project-form-row')).toHaveLength(9);
    const fileInput = container.querySelector('.project-file-dropzone input[type="file"]');
    expect(fileInput).toHaveAttribute('accept', 'image/png,image/jpeg');
    const image = new File(['image'], 'product.png', { type: 'image/png' });
    fireEvent.change(fileInput, { target: { files: [image] } });
    expect(onChange).toHaveBeenCalledWith(expect.objectContaining({ referenceImage: image }));
  });
});
