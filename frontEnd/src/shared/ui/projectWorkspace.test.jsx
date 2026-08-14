import { useState } from 'react';
import { fireEvent, render, screen } from '@testing-library/react';
import { describe, expect, it } from 'vitest';
import { ProjectOptionalField, ProjectOptionalFields, ProjectSplitWorkspace } from './projectWorkspace.jsx';

describe('ProjectSplitWorkspace', () => {
  it('핵심 입력과 보완 입력을 구분된 영역으로 구성한다', () => {
    const { container } = render(<ProjectSplitWorkspace primary={<p>핵심 입력</p>} secondary={<p>보완 입력</p>} />);

    expect(container.querySelector('.project-split-workspace__primary')).toHaveTextContent('핵심 입력');
    expect(container.querySelector('.project-split-workspace__secondary')).toHaveTextContent('보완 입력');
  });
});

describe('ProjectOptionalField', () => {
  it('각 항목 이름과 요약을 접힌 상태에서도 노출하고 접근성 상태를 연결한다', () => {
    function Harness() {
      const [openId, setOpenId] = useState(null);
      return <ProjectOptionalFields completed={1} total={2}>
        <ProjectOptionalField id="region" label="대상 지역" summary="서울" expanded={openId === 'region'} onToggle={() => setOpenId(openId === 'region' ? null : 'region')}>
          <input aria-label="대상 지역 입력" defaultValue="서울" />
        </ProjectOptionalField>
        <ProjectOptionalField id="price" label="가격" summary="아직 입력하지 않음" expanded={openId === 'price'} onToggle={() => setOpenId(openId === 'price' ? null : 'price')}>
          <input aria-label="가격 입력" />
        </ProjectOptionalField>
      </ProjectOptionalFields>;
    }

    render(<Harness />);
    expect(screen.getByRole('heading', { name: '선택 정보' })).toBeInTheDocument();
    expect(screen.getByText('1 / 2 입력')).toBeInTheDocument();
    expect(screen.getByRole('button', { name: /대상 지역.*서울/ })).toHaveAttribute('aria-expanded', 'false');
    expect(screen.getByRole('button', { name: /가격.*아직 입력하지 않음/ })).toBeInTheDocument();

    fireEvent.click(screen.getByRole('button', { name: /대상 지역/ }));
    expect(screen.getByRole('button', { name: /대상 지역/ })).toHaveAttribute('aria-expanded', 'true');
    expect(screen.getByLabelText('대상 지역 입력')).toHaveValue('서울');
  });
});
