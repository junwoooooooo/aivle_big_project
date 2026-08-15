import { describe, expect, it, vi } from 'vitest';
import { fireEvent, render, screen } from '@testing-library/react';

import SampleSizePicker, { SAMPLE_SIZES } from './SampleSizePicker.jsx';

const dominance = [{ taskType: 'DOMINANCE' }];
const price = [{ taskType: 'PRICE' }];

const slider = () => screen.getByLabelText('가상 페르소나 수');

describe('SampleSizePicker — 슬라이더지만 잰 값만 준다', () => {
  it('고른 값을 크게 보여준다', () => {
    render(<SampleSizePicker pairs={dominance} value={100} />);
    expect(screen.getByText('100명')).toBeInTheDocument();
  });

  it('슬라이더는 값이 아니라 인덱스를 다룬다', () => {
    render(<SampleSizePicker pairs={dominance} value={300} />);
    expect(slider()).toHaveValue('2');
    expect(slider()).toHaveAttribute('aria-valuetext', '300명');
  });

  it('움직이면 그 자리의 표본 크기를 올려 보낸다', () => {
    const onChange = vi.fn();
    render(<SampleSizePicker pairs={dominance} value={50} onChange={onChange} />);
    fireEvent.change(slider(), { target: { value: '2' } });
    expect(onChange).toHaveBeenCalledWith(300);
  });

  /**
   * ⚠ 이 검사가 이 부품의 존재 이유다. 서버(`TwinSurveyService.SAMPLE_SIZES`)와
   * AI(`models.py` 의 `SampleSize`)가 50·100·300 만 받고, MDE 표도 그 셋으로만 실측돼 있다.
   * 연속 슬라이더로 바꾸면 화면이 **재본 적 없는 측정 한계**로 답하게 된다.
   */
  it('50·100·300 외의 값이 나올 수 없다', () => {
    const onChange = vi.fn();
    render(<SampleSizePicker pairs={dominance} value={50} onChange={onChange} />);

    expect(slider()).toHaveAttribute('min', '0');
    expect(slider()).toHaveAttribute('max', String(SAMPLE_SIZES.length - 1));
    expect(slider()).toHaveAttribute('step', '1');

    SAMPLE_SIZES.forEach((_size, index) => {
      fireEvent.change(slider(), { target: { value: String(index) } });
    });
    onChange.mock.calls.forEach(([value]) => expect(SAMPLE_SIZES).toContain(value));
  });

  it('응답 수와 예상 시간을 보여준다', () => {
    render(<SampleSizePicker pairs={[{ taskType: 'DOMINANCE' }, { taskType: 'DOMINANCE' }]} value={100} />);
    expect(screen.getByText(/880회 응답/)).toBeInTheDocument();
  });

  it('표본이 부족할 때만 측정 한계를 말한다', () => {
    render(<SampleSizePicker pairs={price} value={50} />);
    expect(screen.getByRole('note').textContent).toContain('«못 잼» 으로 끝난다');
  });

  it('잴 수 있는 표본이면 아무 말도 하지 않는다', () => {
    render(<SampleSizePicker pairs={dominance} value={50} />);
    expect(screen.queryByRole('note')).not.toBeInTheDocument();
  });

  it('자극이 없으면 한계를 지어내지 않는다', () => {
    render(<SampleSizePicker pairs={[]} value={100} />);
    expect(screen.getByText(/비교안을 먼저 정하면/)).toBeInTheDocument();
    expect(screen.queryByRole('note')).not.toBeInTheDocument();
  });

  it('양방향이 설계임을 화면에 남긴다', () => {
    render(<SampleSizePicker pairs={dominance} value={100} />);
    expect(screen.getByText(/양방향 제시는 옵션이 아니라 설계다/)).toBeInTheDocument();
  });

  it('disabled 면 움직일 수 없다', () => {
    render(<SampleSizePicker pairs={dominance} value={100} disabled />);
    expect(slider()).toBeDisabled();
  });
});
