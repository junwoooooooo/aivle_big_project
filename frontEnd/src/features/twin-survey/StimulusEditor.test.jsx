import { describe, expect, it, vi } from 'vitest';
import { fireEvent, render, screen } from '@testing-library/react';
import StimulusEditor from './StimulusEditor.jsx';

const pair = (overrides = {}) => ({
  pairId: 'P1',
  X: { label: '신선', attrs: { 형태: '신선(냉장)' }, priceKrw: 4500 },
  Y: { label: '냉동', attrs: { 형태: '냉동' }, priceKrw: 4500 },
  ...overrides,
});

const ethicalPair = () => ({
  pairId: 'P2',
  X: { label: '인증', attrs: { 인증: '있음' }, priceKrw: 4500 },
  Y: { label: '무인증', attrs: { 인증: '없음' }, priceKrw: 4500 },
});

describe('StimulusEditor — 판정을 그 자리에서 보여준다', () => {
  it('팔 수 있는 쌍은 유형을 알려준다', () => {
    render(<StimulusEditor pairs={[pair()]} onChange={() => {}} />);
    expect(screen.getByText('명백한 우열형')).toBeInTheDocument();
    expect(screen.queryByRole('alert')).not.toBeInTheDocument();
  });

  it('윤리·가치형은 이유와 함께 막는다', () => {
    render(<StimulusEditor pairs={[ethicalPair()]} onChange={() => {}} />);
    expect(screen.getByText('윤리·가치형 — 제공하지 않음')).toBeInTheDocument();
    expect(screen.getByRole('status').textContent).toContain('예측을 제공하지 않는다');
    expect(screen.getByRole('alert').textContent).toContain('팔 수 없는 질문이 1개');
  });

  it('다속성 경합은 고치는 방법까지 말한다', () => {
    render(<StimulusEditor
      pairs={[pair({
        X: { label: 'A', attrs: { 형태: '신선', 원산지: '칠레산' }, priceKrw: 4500 },
        Y: { label: 'B', attrs: { 형태: '냉동', 원산지: '노르웨이산' }, priceKrw: 4500 },
      })]}
      onChange={() => {}}
    />);
    expect(screen.getByText('측정 불가')).toBeInTheDocument();
    expect(screen.getByRole('status').textContent).toContain('한 번에 한 속성만');
  });

  it('가격형은 통과하되 유형을 드러낸다', () => {
    render(<StimulusEditor
      pairs={[pair({
        X: { label: 'A', attrs: { 형태: '신선' }, priceKrw: 5000 },
        Y: { label: 'B', attrs: { 형태: '냉동' }, priceKrw: 4500 },
      })]}
      onChange={() => {}}
    />);
    expect(screen.getByText('가격형')).toBeInTheDocument();
    expect(screen.queryByRole('alert')).not.toBeInTheDocument();
  });

  it('여러 쌍 중 하나만 막혀도 경고한다', () => {
    render(<StimulusEditor pairs={[pair(), ethicalPair()]} onChange={() => {}} />);
    expect(screen.getByRole('alert').textContent).toContain('1개');
  });
});

describe('StimulusEditor — 편집', () => {
  it('속성 값을 고치면 올려 보낸다', () => {
    const onChange = vi.fn();
    render(<StimulusEditor pairs={[pair()]} onChange={onChange} />);
    fireEvent.change(screen.getByLabelText('P1 형태 Y'), { target: { value: '신선(냉장)' } });

    expect(onChange).toHaveBeenCalledTimes(1);
    expect(onChange.mock.calls[0][0][0].Y.attrs.형태).toBe('신선(냉장)');
  });

  it('가격은 정수로만 올라간다 — 실수는 canonical hash 가 거부한다', () => {
    const onChange = vi.fn();
    render(<StimulusEditor pairs={[pair()]} onChange={onChange} />);
    fireEvent.change(screen.getByLabelText('P1 가격 X'), { target: { value: '5200' } });

    expect(onChange.mock.calls[0][0][0].X.priceKrw).toBe(5200);
    expect(Number.isInteger(onChange.mock.calls[0][0][0].X.priceKrw)).toBe(true);
  });

  it('가격을 비우면 null 이 된다 — 0 으로 지어내지 않는다', () => {
    const onChange = vi.fn();
    render(<StimulusEditor pairs={[pair()]} onChange={onChange} />);
    fireEvent.change(screen.getByLabelText('P1 가격 X'), { target: { value: '' } });

    expect(onChange.mock.calls[0][0][0].X.priceKrw).toBeNull();
  });

  it('상황 문장을 고치면 올려 보낸다', () => {
    const onSituationChange = vi.fn();
    render(<StimulusEditor
      pairs={[pair()]}
      situation="옛 문장"
      onSituationChange={onSituationChange}
      onChange={() => {}}
    />);
    fireEvent.change(screen.getByLabelText('상황 문장'), { target: { value: '새 문장' } });

    expect(onSituationChange).toHaveBeenCalledWith('새 문장');
  });

  it('disabled 면 고칠 수 없다', () => {
    render(<StimulusEditor pairs={[pair()]} onChange={() => {}} disabled />);
    expect(screen.getByLabelText('P1 형태 X')).toBeDisabled();
    expect(screen.getByLabelText('P1 가격 X')).toBeDisabled();
  });

  it('쌍이 없으면 그렇게 말한다', () => {
    render(<StimulusEditor pairs={[]} onChange={() => {}} />);
    expect(screen.getByText(/자극 쌍이 없다/)).toBeInTheDocument();
  });
});
