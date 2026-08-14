import { render } from '@testing-library/react';
import { describe, expect, it } from 'vitest';

import { useBodyScrollLock } from './useBodyScrollLock.js';

function Lock({ active }) {
  useBodyScrollLock(active);
  return null;
}

describe('useBodyScrollLock', () => {
  it('원래 overflow를 보존하고 마지막 잠금이 해제될 때만 복원한다', () => {
    document.body.style.overflow = 'auto';
    const first = render(<Lock active />);
    const second = render(<Lock active />);
    expect(document.body.style.overflow).toBe('hidden');
    first.unmount();
    expect(document.body.style.overflow).toBe('hidden');
    second.unmount();
    expect(document.body.style.overflow).toBe('auto');
    document.body.style.overflow = '';
  });
});
