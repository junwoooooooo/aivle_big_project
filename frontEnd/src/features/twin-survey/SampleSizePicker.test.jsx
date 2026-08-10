import { describe, expect, it, vi } from 'vitest';
import { fireEvent, render, screen } from '@testing-library/react';
import SampleSizePicker from './SampleSizePicker.jsx';

const DOMINANCE = { taskType: 'DOMINANCE' };
const PRICE = { taskType: 'PRICE' };

describe('SampleSizePicker', () => {
  it('세 선택지를 준다', () => {
    render(<SampleSizePicker pairs={[DOMINANCE]} value={100} onChange={() => {}} />);
    expect(screen.getByLabelText(/50명/)).toBeInTheDocument();
    expect(screen.getByLabelText(/100명/)).toBeInTheDocument();
    expect(screen.getByLabelText(/300명/)).toBeInTheDocument();
  });

  it('고른 값이 선택돼 있다', () => {
    render(<SampleSizePicker pairs={[DOMINANCE]} value={300} onChange={() => {}} />);
    expect(screen.getByLabelText(/300명/)).toBeChecked();
    expect(screen.getByLabelText(/50명/)).not.toBeChecked();
  });

  it('고르면 값을 올려 보낸다', () => {
    const onChange = vi.fn();
    render(<SampleSizePicker pairs={[DOMINANCE]} value={100} onChange={onChange} />);
    fireEvent.click(screen.getByLabelText(/300명/));
    expect(onChange).toHaveBeenCalledWith(300);
  });

  // 안내 문단에도 같은 말이 나오므로 «숫자가 붙은» 항목만 센다.
  const MDE_ITEM = /못 재는 최소 차이 \d/;

  it('각 선택지가 못 재는 최소 차이를 같이 보여준다', () => {
    render(<SampleSizePicker pairs={[PRICE]} value={100} onChange={() => {}} />);
    expect(screen.getAllByText(MDE_ITEM)).toHaveLength(3);
  });

  it('가격형 n=50 은 «못 잼» 경고를 그 자리에 붙인다', () => {
    render(<SampleSizePicker pairs={[PRICE]} value={100} onChange={() => {}} />);
    const notes = screen.getAllByRole('note');
    expect(notes.length).toBeGreaterThan(0);
    expect(notes[0].textContent).toContain('«못 잼»');
  });

  it('우열형만이면 경고가 없다', () => {
    render(<SampleSizePicker pairs={[DOMINANCE]} value={100} onChange={() => {}} />);
    expect(screen.queryAllByRole('note')).toHaveLength(0);
  });

  it('응답 수와 예상 시간을 보여준다', () => {
    render(<SampleSizePicker pairs={[DOMINANCE, PRICE]} value={100} onChange={() => {}} />);
    expect(screen.getByText(/880회 응답/)).toBeInTheDocument();
  });

  it('양방향이 설계임을 화면에 남긴다', () => {
    render(<SampleSizePicker pairs={[DOMINANCE]} value={100} onChange={() => {}} />);
    expect(screen.getByText(/양방향 제시는 옵션이 아니라 설계다/)).toBeInTheDocument();
  });

  it('자극이 없으면 한계를 지어내지 않는다', () => {
    render(<SampleSizePicker pairs={[]} value={100} onChange={() => {}} />);
    expect(screen.getAllByText(/자극을 먼저 확정하면/)).toHaveLength(3);
    expect(screen.queryAllByText(MDE_ITEM)).toHaveLength(0);
  });

  it('disabled 면 고를 수 없다', () => {
    render(<SampleSizePicker pairs={[DOMINANCE]} value={100} onChange={() => {}} disabled />);
    expect(screen.getByLabelText(/300명/)).toBeDisabled();
  });
});
