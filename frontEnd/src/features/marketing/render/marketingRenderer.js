function roundedRect(context, x, y, width, height, radius) {
  const safeRadius = Math.min(radius, width / 2, height / 2);
  context.beginPath();
  context.roundRect(x, y, width, height, safeRadius);
}

function parseGradient(value, fallback) {
  const colors = String(value || '').split(',').filter((color) => /^#[\da-f]{6}$/i.test(color));
  return colors.length >= 2 ? colors.slice(0, 2) : [fallback, '#17363a'];
}

function drawBackground(context, width, height, draft) {
  if (draft.backgroundType === 'GRADIENT') {
    const [start, end] = parseGradient(draft.backgroundValue, draft.accentColor);
    const gradient = context.createLinearGradient(0, 0, width, height);
    gradient.addColorStop(0, start);
    gradient.addColorStop(1, end);
    context.fillStyle = gradient;
  } else {
    context.fillStyle = draft.backgroundType === 'SOLID'
      ? draft.backgroundValue
      : draft.textColor === '#ffffff' ? '#17363a' : '#f7f7f2';
  }
  context.fillRect(0, 0, width, height);
  if (draft.backgroundType === 'PATTERN') {
    context.globalAlpha = 0.12;
    context.strokeStyle = draft.accentColor;
    context.lineWidth = Math.max(2, width * 0.003);
    for (let offset = -height; offset < width; offset += width * 0.09) {
      context.beginPath();
      context.moveTo(offset, 0);
      context.lineTo(offset + height, height);
      context.stroke();
    }
    context.globalAlpha = 1;
  }
}

function wordsFor(text) {
  const value = String(text || '');
  if (typeof Intl?.Segmenter === 'function') {
    return Array.from(
      new Intl.Segmenter('ko', { granularity: 'word' }).segment(value),
      (part) => part.segment,
    ).filter((part) => part.trim());
  }
  return value.split(/\s+/).filter(Boolean);
}

function allLinesFor(context, text, maxWidth) {
  const words = wordsFor(text);
  const lines = [];
  let line = '';
  words.forEach((word) => {
    const candidate = line ? `${line} ${word}` : word;
    if (context.measureText(candidate).width <= maxWidth || !line) {
      line = candidate;
    } else {
      lines.push(line);
      line = word;
    }
  });
  if (line) lines.push(line);
  return lines;
}

function linesFor(context, text, maxWidth, maxLines) {
  const lines = allLinesFor(context, text, maxWidth);
  if (lines.length <= maxLines) return lines;
  const visible = lines.slice(0, maxLines);
  let last = visible[maxLines - 1];
  while (last.length > 1 && context.measureText(`${last}…`).width > maxWidth) {
    last = last.slice(0, -1);
  }
  visible[maxLines - 1] = `${last}…`;
  return visible;
}

export async function marketingOverflowWarnings(content) {
  await document.fonts?.ready;
  const width = content.content.width;
  const height = content.content.height;
  const draft = content.current;
  const canvas = document.createElement('canvas');
  const context = canvas.getContext('2d');
  const scale = Math.min(width, height) / 1080;
  const inset = Math.max(48 * scale, width * 0.07);
  const layoutWidth = draft.layoutTemplate === 'SPLIT_VISUAL'
    ? width * 0.46 : width - inset * 2;
  const headlineSize = Math.min(
    Math.max(28, Number(draft.headlineSize) || 72) * scale,
    Math.max(48 * scale, width * 0.09),
  );
  const warnings = [];
  context.font = `700 ${headlineSize}px Inter, Pretendard, sans-serif`;
  if (allLinesFor(context, draft.headline, layoutWidth).length > 4) {
    warnings.push('Headline이 4줄을 초과합니다.');
  }
  context.font = `500 ${30 * scale}px Inter, Pretendard, sans-serif`;
  if (allLinesFor(context, draft.subheadline, layoutWidth).length > 3) {
    warnings.push('Subheadline이 3줄을 초과합니다.');
  }
  context.font = `400 ${21 * scale}px Inter, Pretendard, sans-serif`;
  if (allLinesFor(context, draft.bodyCopy, layoutWidth).length > 5) {
    warnings.push('본문이 5줄을 초과합니다.');
  }
  context.font = `700 ${22 * scale}px Inter, Pretendard, sans-serif`;
  if (context.measureText(draft.callToAction || '').width > Math.min(width * 0.38, 290 * scale) - 30 * scale) {
    warnings.push('CTA 문구가 버튼 폭을 초과합니다.');
  }
  return warnings;
}

function drawTextBlock(context, text, {
  x,
  y,
  maxWidth,
  fontSize,
  lineHeight = 1.12,
  maxLines = 4,
  weight = 700,
  color,
  align = 'left',
}) {
  context.save();
  context.fillStyle = color;
  context.font = `${weight} ${fontSize}px Inter, Pretendard, sans-serif`;
  context.textAlign = align;
  context.textBaseline = 'top';
  const lines = linesFor(context, text, maxWidth, maxLines);
  lines.forEach((line, index) => {
    context.fillText(line, x, y + index * fontSize * lineHeight, maxWidth);
  });
  context.restore();
  return lines.length * fontSize * lineHeight;
}

function alignment(draft, width, inset) {
  if (draft.textAlignment === 'CENTER') return { x: width / 2, align: 'center' };
  if (draft.textAlignment === 'RIGHT') return { x: width - inset, align: 'right' };
  return { x: inset, align: 'left' };
}

function drawTag(context, text, x, y, scale, colors, align) {
  context.font = `600 ${18 * scale}px Inter, Pretendard, sans-serif`;
  const padding = 14 * scale;
  const width = context.measureText(text).width + padding * 2;
  const height = 38 * scale;
  const left = align === 'center' ? x - width / 2 : align === 'right' ? x - width : x;
  context.fillStyle = colors.accent;
  roundedRect(context, left, y, width, height, 19 * scale);
  context.fill();
  context.fillStyle = colors.background;
  context.textAlign = 'center';
  context.textBaseline = 'middle';
  context.fillText(text, left + width / 2, y + height / 2);
}

export function drawMarketingContent(context, width, height, content) {
  const draft = content.current ?? content;
  const scale = Math.min(width, height) / 1080;
  const inset = Math.max(48 * scale, width * 0.07);
  const colors = {
    accent: draft.accentColor || '#0f8878',
    text: draft.textColor || '#ffffff',
    background: draft.backgroundType === 'SOLID'
      ? draft.backgroundValue
      : '#17363a',
  };
  drawBackground(context, width, height, draft);

  if (draft.layoutTemplate === 'SPLIT_VISUAL') {
    context.globalAlpha = 0.18;
    context.fillStyle = colors.accent;
    context.fillRect(width * 0.57, 0, width * 0.43, height);
    context.globalAlpha = 1;
  } else if (draft.layoutTemplate === 'EDITORIAL_POSTER') {
    context.fillStyle = colors.accent;
    context.fillRect(inset, inset, width * 0.12, Math.max(10 * scale, height * 0.012));
  } else if (draft.layoutTemplate === 'MINIMAL_CARD') {
    context.fillStyle = draft.textColor === '#ffffff' ? '#ffffff18' : '#ffffff';
    roundedRect(context, inset * 0.65, inset * 0.65, width - inset * 1.3, height - inset * 1.3, 34 * scale);
    context.fill();
  }

  const layoutWidth = draft.layoutTemplate === 'SPLIT_VISUAL'
    ? width * 0.46
    : width - inset * 2;
  const aligned = draft.layoutTemplate === 'SPLIT_VISUAL'
    ? { x: inset, align: 'left' }
    : alignment(draft, width, inset);
  let cursor = draft.layoutTemplate === 'EDITORIAL_POSTER' ? height * 0.17 : height * 0.2;

  if (draft.showPersonaTag) {
    drawTag(context, '검증 Persona', aligned.x, cursor, scale, colors, aligned.align);
    cursor += 64 * scale;
  }
  const headlineSize = Math.min(
    Math.max(28, Number(draft.headlineSize) || 72) * scale,
    Math.max(48 * scale, width * 0.09),
  );
  cursor += drawTextBlock(context, draft.headline, {
    x: aligned.x,
    y: cursor,
    maxWidth: layoutWidth,
    fontSize: headlineSize,
    maxLines: 4,
    color: colors.text,
    align: aligned.align,
  });
  cursor += 28 * scale;
  cursor += drawTextBlock(context, draft.subheadline, {
    x: aligned.x,
    y: cursor,
    maxWidth: layoutWidth,
    fontSize: 30 * scale,
    maxLines: 3,
    weight: 500,
    color: colors.text,
    align: aligned.align,
  });
  cursor += 24 * scale;
  drawTextBlock(context, draft.bodyCopy, {
    x: aligned.x,
    y: cursor,
    maxWidth: layoutWidth,
    fontSize: 21 * scale,
    maxLines: 5,
    weight: 400,
    color: colors.text,
    align: aligned.align,
  });

  if (draft.showCta && draft.callToAction) {
    const ctaWidth = Math.min(width * 0.38, 290 * scale);
    const ctaHeight = 64 * scale;
    const left = aligned.align === 'center'
      ? aligned.x - ctaWidth / 2
      : aligned.align === 'right' ? aligned.x - ctaWidth : aligned.x;
    context.fillStyle = colors.accent;
    roundedRect(context, left, height - inset - ctaHeight, ctaWidth, ctaHeight, 14 * scale);
    context.fill();
    context.fillStyle = colors.background;
    context.font = `700 ${22 * scale}px Inter, Pretendard, sans-serif`;
    context.textAlign = 'center';
    context.textBaseline = 'middle';
    context.fillText(draft.callToAction, left + ctaWidth / 2, height - inset - ctaHeight / 2);
  }
  if (draft.supportingText) {
    drawTextBlock(context, draft.supportingText, {
      x: width - inset,
      y: height - inset * 0.55,
      maxWidth: width - inset * 2,
      fontSize: 14 * scale,
      maxLines: 1,
      weight: 400,
      color: colors.text,
      align: 'right',
    });
  }
}

export function renderPreview(canvas, content, maxDimension = 1100) {
  if (!canvas || !content) return;
  const actualWidth = content.content?.width ?? content.width;
  const actualHeight = content.content?.height ?? content.height;
  const ratio = Math.min(1, maxDimension / Math.max(actualWidth, actualHeight));
  canvas.width = Math.max(1, Math.round(actualWidth * ratio));
  canvas.height = Math.max(1, Math.round(actualHeight * ratio));
  const context = canvas.getContext('2d');
  context.clearRect(0, 0, canvas.width, canvas.height);
  drawMarketingContent(context, canvas.width, canvas.height, content);
}

export async function exportMarketingPng(content, filename) {
  await document.fonts?.ready;
  const warnings = await marketingOverflowWarnings(content);
  if (warnings.length > 0) {
    const error = new Error(warnings.join(' '));
    error.code = 'MARKETING_EXPORT_OVERFLOW';
    error.warnings = warnings;
    throw error;
  }
  const canvas = document.createElement('canvas');
  canvas.width = content.content.width;
  canvas.height = content.content.height;
  drawMarketingContent(canvas.getContext('2d'), canvas.width, canvas.height, content);
  const blob = await new Promise((resolve, reject) => {
    canvas.toBlob((value) => value ? resolve(value) : reject(new Error('PNG 변환 실패')), 'image/png');
  });
  const url = URL.createObjectURL(blob);
  const link = document.createElement('a');
  link.href = url;
  link.download = `${filename || 'marketing-content'}.png`;
  link.click();
  URL.revokeObjectURL(url);
}
