import { describe, expect, it } from 'vitest';
import { catalogByCode, listItemText, parseJsonArray } from './personaViewModel.js';

describe('persona view model', () => {
  it('parses persisted JSON arrays defensively', () => {
    expect(parseJsonArray('["근거"]')).toEqual(['근거']);
    expect(parseJsonArray('{"value":1}')).toEqual([]);
    expect(parseJsonArray('broken')).toEqual([]);
  });

  it('indexes the catalog by stable persona code', () => {
    expect(catalogByCode([{ personaCode: 'KMP25-20-F-01', id: 1 }])
      .get('KMP25-20-F-01').id).toBe(1);
  });

  it('renders persisted structured interview and survey questions safely', () => {
    expect(listItemText({ order: 1, question: '최근 경험을 설명해 주세요.' }))
      .toBe('최근 경험을 설명해 주세요.');
    expect(listItemText({ rawPrompt: 'do not render' })).toBe('');
  });
});
