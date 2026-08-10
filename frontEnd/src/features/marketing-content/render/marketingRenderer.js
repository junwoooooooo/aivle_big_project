export function marketingText(result) {
  return [result?.title, result?.body, result?.callToAction,
    result?.hashtags?.map((tag) => tag.startsWith('#') ? tag : `#${tag}`).join(' '),
    result?.imageBrief && `이미지 설명\n${result.imageBrief}`,
    result?.legalReview?.requiredDisclosuresApplied?.length && `필수 고지\n${result.legalReview.requiredDisclosuresApplied.join('\n')}`,
  ].filter(Boolean).join('\n\n');
}

export async function copyMarketingContent(result) {
  const value = marketingText(result);
  await navigator.clipboard.writeText(value);
  return value;
}

export function downloadMarketingContent(result, filename = 'marketing-content') {
  const blob = new Blob([marketingText(result)], { type: 'text/plain;charset=utf-8' });
  const url = URL.createObjectURL(blob); const link = document.createElement('a');
  link.href = url; link.download = `${filename.replace(/[^\p{L}\p{N}._-]+/gu, '-')}.txt`; link.click(); URL.revokeObjectURL(url);
}
