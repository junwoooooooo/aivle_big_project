import { readFileSync } from 'node:fs';
import { dirname, resolve } from 'node:path';
import { fileURLToPath } from 'node:url';

import { describe, expect, it, vi } from 'vitest';
import { fireEvent, render, screen } from '@testing-library/react';

import StimulusDraftPicker from './StimulusDraftPicker.jsx';
import { gateSurvey } from './taskTypeGate.js';

/** AI·백엔드 계약 테스트가 **같이** 읽는 파일이다. 한쪽만 고치면 반대쪽이 빨개진다. */
const GOLDEN = JSON.parse(readFileSync(resolve(
  dirname(fileURLToPath(import.meta.url)),
  '../../../../ai/tests/fixtures/twin_survey/stimulus_draft.json'), 'utf-8'));

describe('StimulusDraftPicker — 첫 칸을 채우는 대신 고른다', () => {
  it('초안 쌍을 축과 이유까지 보여준다', () => {
    render(<StimulusDraftPicker draft={GOLDEN} />);
    expect(screen.getByText('보관 형태')).toBeInTheDocument();
    expect(screen.getByText(/신선 보관형 «냉장 신선» vs 냉동 보관형 «급속 냉동»/))
      .toBeInTheDocument();
  });

  it('고른 쌍만, 조사가 먹는 모양으로 넘긴다', () => {
    const onUse = vi.fn();
    render(<StimulusDraftPicker draft={GOLDEN} onUse={onUse} />);

    fireEvent.click(screen.getAllByRole('checkbox')[1]);   // P2 를 뺀다
    fireEvent.click(screen.getByRole('button', { name: /고른 1쌍으로 계속/ }));

    const [situation, pairs] = onUse.mock.calls[0];
    expect(situation).toBe(GOLDEN.situation);
    expect(pairs).toHaveLength(1);
    // `axis`·`rationale` 은 조사 입력 모델이 모르는 필드다 — 그대로 보내면 400 이다.
    expect(Object.keys(pairs[0]).sort()).toEqual(['X', 'Y', 'pairId']);
  });

  it('넘긴 쌍은 화면 게이트를 그대로 통과한다 — 초안을 고르면 바로 실행할 수 있다', () => {
    const onUse = vi.fn();
    render(<StimulusDraftPicker draft={GOLDEN} onUse={onUse} />);
    fireEvent.click(screen.getByRole('button', { name: /고른 2쌍으로 계속/ }));

    expect(gateSurvey(onUse.mock.calls[0][1]).canRun).toBe(true);
  });

  it('아무것도 안 고르면 계속할 수 없다', () => {
    render(<StimulusDraftPicker draft={GOLDEN} />);
    screen.getAllByRole('checkbox').forEach((box) => fireEvent.click(box));
    expect(screen.getByRole('button', { name: /고른 0쌍으로 계속/ })).toBeDisabled();
  });

  it('버린 축을 감추지 않는다 — 왜 못 묻는지 봐야 컨셉을 고친다', () => {
    render(<StimulusDraftPicker draft={GOLDEN} />);
    expect(screen.getByText('못 묻는 축 1개')).toBeInTheDocument();
    expect(screen.getByText(/윤리·가치 속성이 대비의 축이다/)).toBeInTheDocument();
  });
});
