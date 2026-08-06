import { useMemo } from 'react';

const commonPasswords = new Set([
  'password', 'password123', 'passwordpassword', 'qwerty', 'qwerty123',
  '123456789', '1234567890', 'letmein', 'welcome123', 'ventureverify',
  'ventureverify123', 'venture-verify', 'testtesttesttest', 'adminadmin',
]);
const keyboardSequences = ['qwerty', 'asdf', 'zxcv', '123456', '012345', '987654'];
const graphemes = (value) => typeof Intl !== 'undefined' && Intl.Segmenter
  ? Array.from(new Intl.Segmenter('ko', { granularity: 'grapheme' }).segment(value), ({ segment }) => segment)
  : Array.from(value);
const comparable = (value) => value.normalize('NFKC').toLocaleLowerCase().replace(/[\s._-]/gu, '');

function hasRepeatedCharacters(value) {
  return /(.)\1{5,}/u.test(value);
}
function hasRepeatedSubstring(value) {
  for (let size = 2; size <= Math.min(8, Math.floor(value.length / 3)); size += 1) {
    const unit = value.slice(0, size);
    if (unit && value === unit.repeat(value.length / size)) return true;
  }
  return false;
}
function hasSequentialPattern(value) {
  return keyboardSequences.some((pattern) => value.includes(pattern) || value.includes([...pattern].reverse().join('')));
}

export default function usePasswordChecks(password, confirmPassword, username = '', displayName = '') {
  return useMemo(() => {
    const hasInput = password.length > 0;
    const folded = password.toLocaleLowerCase();
    const comparison = comparable(password);
    const normalizedUsername = comparable(username);
    const normalizedName = comparable(displayName.trim());
    const matchesUsername = normalizedUsername.length >= 4 && comparison.includes(normalizedUsername);
    const matchesDisplayName = graphemes(normalizedName).length >= 4 && comparison.includes(normalizedName);
    const isCommonPassword = commonPasswords.has(folded) || commonPasswords.has(comparison);
    const hasRepeatedPattern = hasRepeatedCharacters(comparison) || hasRepeatedSubstring(comparison);
    const hasSequentialPatternValue = hasSequentialPattern(comparison);
    const looksWeak = isCommonPassword || matchesUsername || matchesDisplayName || hasRepeatedPattern || hasSequentialPatternValue;
    return {
      confirmationMatches: confirmPassword.length > 0 && password === confirmPassword,
      hasInput,
      hasMinimumLength: hasInput && graphemes(password).length >= 15,
      hasRepeatedPattern,
      hasSequentialPattern: hasSequentialPatternValue,
      isCommonPassword,
      isNotCommonOrSimilar: hasInput && !looksWeak,
      isValid: hasInput && graphemes(password).length >= 15 && graphemes(password).length <= 64 && !looksWeak,
      isWithinMaximumLength: hasInput && graphemes(password).length <= 64,
      matchesDisplayName,
      matchesUsername,
      remainingMinimumCharacters: Math.max(0, 15 - graphemes(password).length),
    };
  }, [confirmPassword, displayName, password, username]);
}
