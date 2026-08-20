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

const EXPORT_WIDTH = 1080;
const MAX_IMAGE_HEIGHT = 1080;
const EXPORT_PADDING = 72;
const FONT_FAMILY = 'Pretendard, "Noto Sans KR", "Apple SD Gothic Neo", sans-serif';

function loadImage(source) {
  return new Promise((resolve, reject) => {
    const image = new Image();
    image.onload = () => resolve(image);
    image.onerror = () => reject(new Error('생성 이미지를 불러오지 못했습니다.'));
    image.src = source;
  });
}

function splitLongToken(context, token, maxWidth) {
  const chunks = [];
  let current = '';
  [...token].forEach((character) => {
    const candidate = `${current}${character}`;
    if (current && context.measureText(candidate).width > maxWidth) {
      chunks.push(current);
      current = character;
    } else {
      current = candidate;
    }
  });
  if (current) chunks.push(current);
  return chunks;
}

export function wrapMarketingText(context, value, maxWidth) {
  const lines = [];
  String(value ?? '').split('\n').forEach((paragraph) => {
    if (!paragraph) {
      lines.push('');
      return;
    }
    const tokens = paragraph.split(/\s+/).flatMap((token) => (
      context.measureText(token).width > maxWidth
        ? splitLongToken(context, token, maxWidth)
        : [token]
    ));
    let current = '';
    tokens.forEach((token) => {
      const candidate = current ? `${current} ${token}` : token;
      if (current && context.measureText(candidate).width > maxWidth) {
        lines.push(current);
        current = token;
      } else {
        current = candidate;
      }
    });
    if (current) lines.push(current);
  });
  return lines;
}

function imageLayout(image) {
  const naturalWidth = image.naturalWidth || image.width;
  const naturalHeight = image.naturalHeight || image.height;
  if (!naturalWidth || !naturalHeight) throw new Error('생성 이미지의 크기를 확인하지 못했습니다.');
  const scale = Math.min(EXPORT_WIDTH / naturalWidth, MAX_IMAGE_HEIGHT / naturalHeight);
  const width = naturalWidth * scale;
  const height = naturalHeight * scale;
  return { x: (EXPORT_WIDTH - width) / 2, width, height };
}

function setFont(context, weight, size) {
  context.font = `${weight} ${size}px ${FONT_FAMILY}`;
}

function drawLines(context, lines, x, y, lineHeight, align) {
  context.textAlign = align === 'CENTER' ? 'center' : 'left';
  lines.forEach((line, index) => context.fillText(line, x, y + (lineHeight * index)));
  return y + (lineHeight * lines.length);
}

function roundedRect(context, x, y, width, height, radius) {
  const r = Math.min(radius, width / 2, height / 2);
  context.beginPath();
  context.moveTo(x + r, y);
  context.lineTo(x + width - r, y);
  context.quadraticCurveTo(x + width, y, x + width, y + r);
  context.lineTo(x + width, y + height - r);
  context.quadraticCurveTo(x + width, y + height, x + width - r, y + height);
  context.lineTo(x + r, y + height);
  context.quadraticCurveTo(x, y + height, x, y + height - r);
  context.lineTo(x, y + r);
  context.quadraticCurveTo(x, y, x + r, y);
  context.closePath();
}

function canvasBlob(canvas) {
  return new Promise((resolve, reject) => {
    canvas.toBlob((blob) => {
      if (blob) resolve(blob);
      else reject(new Error('콘텐츠 이미지를 만들지 못했습니다.'));
    }, 'image/png');
  });
}

function safeFilename(filename) {
  return String(filename || 'marketing-content')
    .replace(/\.(?:txt|png|jpe?g)$/i, '')
    .replace(/[^\p{L}\p{N}._-]+/gu, '-')
    .replace(/^-+|-+$/g, '') || 'marketing-content';
}

export async function downloadMarketingContent(result, artifactUrl, style, filename = 'marketing-content') {
  if (!artifactUrl) throw new Error('다운로드할 생성 이미지가 아직 준비되지 않았습니다.');
  if (document.fonts?.ready) await document.fonts.ready.catch(() => {});
  const image = await loadImage(artifactUrl);
  const imageBox = imageLayout(image);
  const scale = Math.min(1.25, Math.max(0.85, Number(style?.scale) || 1));
  const align = style?.align === 'CENTER' ? 'CENTER' : 'LEFT';
  const accent = style?.accent || '#0f8878';
  const textWidth = EXPORT_WIDTH - (EXPORT_PADDING * 2);
  const textX = align === 'CENTER' ? EXPORT_WIDTH / 2 : EXPORT_PADDING;

  const measureCanvas = document.createElement('canvas');
  const measure = measureCanvas.getContext('2d');
  if (!measure) throw new Error('브라우저에서 이미지 합성을 지원하지 않습니다.');
  setFont(measure, 800, 26 * scale);
  const typeLines = wrapMarketingText(measure, String(result?.contentType || 'MARKETING CONTENT').replaceAll('_', ' '), textWidth);
  setFont(measure, 800, 52 * scale);
  const titleLines = wrapMarketingText(measure, result?.title || 'Headline', textWidth);
  setFont(measure, 400, 30 * scale);
  const bodyLines = wrapMarketingText(measure, result?.body || '', textWidth);
  setFont(measure, 700, 27 * scale);
  const hashtagLines = wrapMarketingText(measure, (result?.hashtags || [])
    .map((tag) => `#${String(tag).replace(/^#/, '')}`).join(' '), textWidth);
  setFont(measure, 400, 22 * scale);
  const disclosureLines = wrapMarketingText(measure,
    result?.legalReview?.requiredDisclosuresApplied?.join(' · ') || '', textWidth);

  const typeHeight = typeLines.length * (34 * scale);
  const titleHeight = titleLines.length * (62 * scale);
  const bodyHeight = bodyLines.length * (46 * scale);
  const hashtagHeight = hashtagLines.length * (38 * scale);
  const disclosureHeight = disclosureLines.length * (32 * scale);
  const ctaHeight = result?.callToAction ? 70 * scale : 0;
  const contentHeight = EXPORT_PADDING + (26 * scale) + typeHeight + 24
    + (52 * scale) + titleHeight
    + (bodyLines.length ? 28 + (30 * scale) + bodyHeight : 0)
    + (result?.callToAction ? 34 + ctaHeight : 0)
    + (hashtagLines.length ? 34 + (27 * scale) + hashtagHeight : 0)
    + (disclosureLines.length ? 68 + (22 * scale) + disclosureHeight : 0)
    + EXPORT_PADDING;

  const canvas = document.createElement('canvas');
  canvas.width = EXPORT_WIDTH;
  canvas.height = Math.ceil(imageBox.height + contentHeight);
  const context = canvas.getContext('2d');
  if (!context) throw new Error('브라우저에서 이미지 합성을 지원하지 않습니다.');

  context.fillStyle = '#e9edf0';
  context.fillRect(0, 0, EXPORT_WIDTH, imageBox.height);
  context.drawImage(image, imageBox.x, 0, imageBox.width, imageBox.height);
  context.fillStyle = '#ffffff';
  context.fillRect(0, imageBox.height, EXPORT_WIDTH, contentHeight);

  let y = imageBox.height + EXPORT_PADDING;
  context.fillStyle = '#45635f';
  setFont(context, 800, 26 * scale);
  y = drawLines(context, typeLines, textX, y + (26 * scale), 34 * scale, align) + 24;
  context.fillStyle = '#17363a';
  setFont(context, 800, 52 * scale);
  y = drawLines(context, titleLines, textX, y + (52 * scale), 62 * scale, align);

  if (bodyLines.length) {
    y += 28;
    context.fillStyle = '#284b4d';
    setFont(context, 400, 30 * scale);
    y = drawLines(context, bodyLines, textX, y + (30 * scale), 46 * scale, align);
  }

  if (result?.callToAction) {
    y += 34;
    setFont(context, 700, 28 * scale);
    const ctaText = String(result.callToAction);
    const ctaWidth = Math.min(textWidth, context.measureText(ctaText).width + (56 * scale));
    const ctaX = align === 'CENTER' ? (EXPORT_WIDTH - ctaWidth) / 2 : EXPORT_PADDING;
    context.fillStyle = accent;
    roundedRect(context, ctaX, y, ctaWidth, ctaHeight, ctaHeight / 2);
    context.fill();
    context.fillStyle = '#ffffff';
    context.textAlign = 'center';
    context.textBaseline = 'middle';
    context.fillText(ctaText, ctaX + (ctaWidth / 2), y + (ctaHeight / 2));
    context.textBaseline = 'alphabetic';
    y += ctaHeight;
  }

  if (hashtagLines.length) {
    y += 34;
    context.fillStyle = accent;
    setFont(context, 700, 27 * scale);
    y = drawLines(context, hashtagLines, textX, y + (27 * scale), 38 * scale, align);
  }

  if (disclosureLines.length) {
    y += 40;
    context.strokeStyle = '#d8e1df';
    context.beginPath();
    context.moveTo(EXPORT_PADDING, y);
    context.lineTo(EXPORT_WIDTH - EXPORT_PADDING, y);
    context.stroke();
    y += 28;
    context.fillStyle = '#607470';
    setFont(context, 400, 22 * scale);
    drawLines(context, disclosureLines, textX, y + (22 * scale), 32 * scale, align);
  }

  const blob = await canvasBlob(canvas);
  const url = URL.createObjectURL(blob);
  const link = document.createElement('a');
  link.href = url;
  link.download = `${safeFilename(filename)}.png`;
  document.body.appendChild(link);
  link.click();
  link.remove();
  URL.revokeObjectURL(url);
}
