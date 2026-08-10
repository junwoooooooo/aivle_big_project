import { fireEvent, render, screen } from '@testing-library/react';
import { describe, expect, it, vi } from 'vitest';
import MarketingCopyEditor from './MarketingCopyEditor.jsx';
import { emptyResult } from '../model/marketingContentModel.js';

describe('MarketingCopyEditor', () => {
  it('creates a shortened revision draft without mutating the generated result', () => {
    const original = { ...emptyResult(), title: '가'.repeat(70), body: '나'.repeat(400) };
    const onChange = vi.fn(); const onRevisionType = vi.fn();
    render(<MarketingCopyEditor value={original} source={{ requiredDisclosures: [] }} onChange={onChange} onRevisionType={onRevisionType} />);
    fireEvent.click(screen.getByRole('button', { name: '짧은 문구로 다듬기' }));
    expect(onRevisionType).toHaveBeenCalledWith('SHORTENED');
    expect(onChange.mock.calls[0][0].title).toHaveLength(45);
    expect(onChange.mock.calls[0][0].body).toHaveLength(240);
    expect(original.title).toHaveLength(70);
  });
});
