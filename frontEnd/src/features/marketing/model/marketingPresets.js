export const PURPOSES = [
  ['AWARENESS', '브랜드·서비스 인지도'],
  ['PRODUCT_INTRODUCTION', '제품·서비스 소개'],
  ['EVENT_PROMOTION', '이벤트 홍보'],
  ['LEAD_GENERATION', '상담·신청 유도'],
  ['CONVERSION', '구매 전환'],
  ['RETENTION', '재방문·재구매'],
];

export const CHANNELS = [
  ['SOCIAL', '소셜 미디어'],
  ['DISPLAY_AD', '디스플레이 광고'],
  ['WEB_BANNER', '웹 배너'],
  ['PRINT_POSTER', '인쇄 포스터'],
  ['PRESENTATION', '프레젠테이션'],
  ['CUSTOM', '기타'],
];

export const FORMATS = [
  ['SQUARE_1080', '1080 × 1080 · 정사각형 SNS', 1080, 1080],
  ['PORTRAIT_1080_1350', '1080 × 1350 · 세로형 SNS', 1080, 1350],
  ['LANDSCAPE_1200_628', '1200 × 628 · 가로형 광고', 1200, 628],
  ['WIDE_1920_1080', '1920 × 1080 · 와이드', 1920, 1080],
  ['STORY_1080_1920', '1080 × 1920 · 스토리', 1080, 1920],
  ['A4_PORTRAIT', '2480 × 3508 · A4 포스터', 2480, 3508],
  ['CUSTOM', '사용자 지정', 1080, 1080],
];

export const TONES = [
  ['TRUSTWORTHY', '신뢰감 있는'],
  ['PROFESSIONAL', '전문적인'],
  ['FRIENDLY', '친근한'],
  ['ENERGETIC', '활기찬'],
  ['EMOTIONAL', '감성적인'],
  ['MINIMAL', '미니멀한'],
  ['PREMIUM', '프리미엄'],
  ['BOLD', '대담한'],
];

export const TEMPLATES = [
  ['HERO_CENTER', 'Hero Center', '강한 중앙 제목과 CTA'],
  ['SPLIT_VISUAL', 'Split Visual', '텍스트와 비주얼의 좌우 분할'],
  ['EDITORIAL_POSTER', 'Editorial Poster', '포스터형 큰 제목과 설명'],
  ['MINIMAL_CARD', 'Minimal Card', '여백 중심의 미니멀 광고'],
];

export const STYLE_PRESETS = [
  {
    id: 'TRUST',
    label: '신뢰형',
    layoutTemplate: 'SPLIT_VISUAL',
    backgroundType: 'GRADIENT',
    backgroundValue: '#123f4a,#0f8878',
    accentColor: '#6ee7d2',
    textColor: '#ffffff',
    visualStyle: 'TRUSTWORTHY',
  },
  {
    id: 'ENERGY',
    label: '활력형',
    layoutTemplate: 'HERO_CENTER',
    backgroundType: 'GRADIENT',
    backgroundValue: '#f26b38,#7c3aed',
    accentColor: '#ffd166',
    textColor: '#ffffff',
    visualStyle: 'ENERGETIC',
  },
  {
    id: 'MINIMAL',
    label: '미니멀형',
    layoutTemplate: 'MINIMAL_CARD',
    backgroundType: 'SOLID',
    backgroundValue: '#f7f7f2',
    accentColor: '#0f8878',
    textColor: '#17363a',
    visualStyle: 'MINIMAL',
  },
];

export function formatDimensions(format) {
  const match = FORMATS.find(([value]) => value === format);
  return match ? { width: match[2], height: match[3] } : { width: 1080, height: 1080 };
}

export function optionLabel(options, value) {
  return options.find(([key]) => key === value)?.[1] ?? value;
}
