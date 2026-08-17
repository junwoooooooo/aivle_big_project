const KRW_IN_TEXT = /(?<![\d.,A-Za-z_])([+-]?(?:\d{1,3}(?:,\d{3})+|\d+))\s*(?:원|KRW)(?![A-Za-z0-9_])/gi;
const KRW_NUMBER = new Intl.NumberFormat('ko-KR', { maximumFractionDigits: 0 });

export function formatKrwNarrative(value) {
  if (typeof value !== 'string') return value;
  return value.replace(KRW_IN_TEXT, (_, rawAmount) => {
    const amount = BigInt(rawAmount.replaceAll(',', ''));
    const absolute = amount < 0n ? -amount : amount;
    const formatted = `${KRW_NUMBER.format(amount)}원`;
    if (absolute < 10000n) return formatted;

    const eok = absolute / 100000000n;
    const belowEok = absolute % 100000000n;
    const man = belowEok / 10000n;
    const won = belowEok % 10000n;
    const parts = [];
    if (eok > 0n) parts.push(`${KRW_NUMBER.format(eok)}억`);
    if (man > 0n) parts.push(`${KRW_NUMBER.format(man)}만`);
    if (won > 0n) parts.push(`${KRW_NUMBER.format(won)}원`);
    if (won === 0n) parts[parts.length - 1] += '원';
    const sign = amount < 0n ? '-' : '';
    return `${formatted} (${sign}${parts.join(' ')})`;
  });
}
