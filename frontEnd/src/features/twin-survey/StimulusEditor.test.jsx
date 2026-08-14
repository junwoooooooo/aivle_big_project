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

describe('StimulusEditor — 무엇과 무엇을 비교하는가만 보인다', () => {
  it('축과 양쪽을 카드 한 장에 보여준다', () => {
    render(<StimulusEditor pairs={[pair()]} />);
    expect(screen.getByText('형태')).toBeInTheDocument();
    const card = screen.getByRole('button', { name: /형태 비교안 편집/ });
    expect(card.textContent).toContain('신선 · 신선(냉장)');
    expect(card.textContent).toContain('냉동 · 냉동');
  });

  it('팔 수 있는 쌍은 유형을 알려준다', () => {
    render(<StimulusEditor pairs={[pair()]} />);
    expect(screen.getByText('명백한 우열형')).toBeInTheDocument();
    expect(screen.queryByRole('alert')).not.toBeInTheDocument();
  });

  it('팔 수 없는 쌍은 고칠 곳을 가리킨다', () => {
    render(<StimulusEditor pairs={[ethicalPair()]} />);
    expect(screen.getByText('윤리·가치형 — 제공하지 않음')).toBeInTheDocument();
    expect(screen.getByRole('alert').textContent).toContain('카드를 눌러 고쳐야');
  });

  it('여러 쌍 중 하나만 막혀도 경고한다', () => {
    render(<StimulusEditor pairs={[pair(), ethicalPair()]} />);
    expect(screen.getByRole('alert').textContent).toContain('1개');
  });

  it('카드를 누르면 그 쌍의 인덱스를 올려 보낸다 — 편집은 창에서 한다', () => {
    const onEdit = vi.fn();
    render(<StimulusEditor pairs={[pair(), ethicalPair()]} onEdit={onEdit} />);
    fireEvent.click(screen.getByRole('button', { name: /인증 비교안 편집/ }));
    expect(onEdit).toHaveBeenCalledWith(1);
  });

  it('상황 문장을 고치면 올려 보낸다', () => {
    const onSituationChange = vi.fn();
    render(
      <StimulusEditor
        pairs={[pair()]}
        situation="가게에서 하나를 고릅니다."
        onSituationChange={onSituationChange}
      />,
    );
    fireEvent.change(screen.getByLabelText('상황 문장'), { target: { value: '새 문장' } });
    expect(onSituationChange).toHaveBeenCalledWith('새 문장');
  });

  it('disabled 면 카드를 누를 수 없다', () => {
    render(<StimulusEditor pairs={[pair()]} disabled />);
    expect(screen.getByRole('button', { name: /형태 비교안 편집/ })).toBeDisabled();
  });

  it('쌍이 없으면 그렇게 말한다', () => {
    render(<StimulusEditor pairs={[]} />);
    expect(screen.getByText(/자극 쌍이 없다/)).toBeInTheDocument();
  });

  /**
   * 가격 칸을 없앤 것이 회귀가 아니라 결정임을 못박는다. 편집칸이 있으면 한쪽만 고쳐
   * 지불의사(차단된 유형)를 만들 수 있고, 양쪽 같은 값은 애초에 고칠 것이 없다.
   */
  it('가격 편집칸을 두지 않는다 — 한쪽만 고치면 지불의사가 된다', () => {
    render(<StimulusEditor pairs={[pair()]} />);
    expect(screen.queryByLabelText(/가격/)).not.toBeInTheDocument();
  });
});
