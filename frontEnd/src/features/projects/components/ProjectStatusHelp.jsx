import { useCallback, useEffect, useRef, useState } from 'react';

import { AppIcon } from '../../../shared/ui/icons.jsx';
import { PROJECT_STATUS_VIEW } from '../model/projectWorkflowModel.js';

const CONTEXT_LABELS = Object.freeze({
  workspace: 'WORKSPACE',
  projects: 'PROJECTS',
  overview: 'OVERVIEW',
  plan: 'PLAN',
  review: 'REVIEW',
  validate: 'VALIDATE',
  report: 'REPORT',
});

function buildSlides() {
  return [
    {
      title: 'AREA',
      body: '프로젝트가 현재 위치한 검증 영역을 나타냅니다.',
      items: [
        ['Plan', '사업계획서 준비와 구조화'],
        ['Review', '법률·규제와 사업성 검토'],
        ['Validate', '고객·시장 반응 검증'],
        ['Report', '통합 결과 확인'],
      ],
    },
    {
      title: 'STATUS',
      body: '프로젝트의 전체 진행 상태와 현재 작업 가능 상태를 보여줍니다.',
      items: Object.values(PROJECT_STATUS_VIEW).map((view) => [view.label, '프로젝트 처리 상태']),
    },
    {
      title: '프로젝트 열기',
      body: '프로젝트 행을 선택하면 해당 프로젝트로 이동합니다.',
      note: '더보기에서는 설정과 삭제를 관리할 수 있습니다.',
    },
    {
      title: '다음 행동',
      body: '현재 Area와 Status를 확인하고 이어갈 작업을 선택합니다.',
    },
  ];
}

const SLIDES = buildSlides();

export default function ProjectStatusHelp({ visible = true, context = 'workspace' }) {
  const [open, setOpen] = useState(false);
  const [slideState, setSlideState] = useState({ context, index: 0 });
  const [direction, setDirection] = useState('next');
  const rootRef = useRef(null);
  const slides = SLIDES;
  const slide = slideState.context === context ? slideState.index : 0;
  const contextLabel = CONTEXT_LABELS[context] || CONTEXT_LABELS.workspace;

  const moveTo = useCallback((nextIndex) => {
    const currentIndex = slideState.context === context ? slideState.index : 0;
    setDirection(nextIndex < currentIndex ? 'previous' : 'next');
    setSlideState({ context, index: nextIndex });
  }, [context, slideState]);

  useEffect(() => {
    if (!open) return undefined;

    const onPointerDown = (event) => {
      if (!rootRef.current?.contains(event.target)) setOpen(false);
    };
    const onKeyDown = (event) => {
      if (event.key === 'Escape') setOpen(false);
      if (event.key === 'ArrowLeft' && slide > 0) moveTo(slide - 1);
      if (event.key === 'ArrowRight' && slide < slides.length - 1) moveTo(slide + 1);
    };

    window.addEventListener('pointerdown', onPointerDown);
    window.addEventListener('keydown', onKeyDown);
    return () => {
      window.removeEventListener('pointerdown', onPointerDown);
      window.removeEventListener('keydown', onKeyDown);
    };
  }, [moveTo, open, slide, slides.length]);

  const current = slides[slide];

  return (
    <aside
      ref={rootRef}
      className={`project-status-help ${open ? 'is-open' : ''} ${visible ? '' : 'is-hidden'}`}
      aria-hidden={!visible}
      aria-label="프로젝트 안내"
    >
      <button
        type="button"
        className="project-status-help__trigger"
        aria-expanded={open}
        aria-controls="project-status-help-content"
        onClick={() => setOpen((value) => !value)}
      >
        <span aria-hidden="true">?</span>
        <span>상태 안내</span>
      </button>

      {open && (
        <section id="project-status-help-content" className="project-status-help__content" aria-live="polite">
          <header>
            <p>{contextLabel}</p>
            <span>프로젝트 안내</span>
          </header>
          <div className={`project-status-help__slide project-status-help__slide--${direction}`} key={`${context}-${slide}`}>
            <h2>{current.title}</h2>
            <p>{current.body}</p>
            {current.items && (
              <dl>
                {current.items.map(([label, description]) => (
                  <div key={label}><dt>{label}</dt><dd>{description}</dd></div>
                ))}
              </dl>
            )}
            {current.note && <p className="project-status-help__note">{current.note}</p>}
          </div>
          <footer>
            <button
              type="button"
              className="project-status-help__arrow"
              aria-label="이전 안내"
              disabled={slide === 0}
              onClick={() => moveTo(slide - 1)}
            >
              <AppIcon name="chevronLeft" size={16} />
            </button>
            <span aria-live="polite">{slide + 1} / {slides.length}</span>
            <button
              type="button"
              className="project-status-help__arrow"
              aria-label="다음 안내"
              disabled={slide === slides.length - 1}
              onClick={() => moveTo(slide + 1)}
            >
              <AppIcon name="chevronRight" size={16} />
            </button>
          </footer>
        </section>
      )}
    </aside>
  );
}
