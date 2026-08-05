import { useEffect, useRef, useState } from 'react';
import './ReportPreview.css'; // Auto-fit 스타일링

export const ReportPreview = ({ reportData }) => {
  const reportRef = useRef(null);
  const [scale, setScale] = useState(1);

  // 화면 크기에 맞게 보고서 전체 비율 자동 축소/확대 (Responsive Preview)
  useEffect(() => {
    const updateScale = () => {
      if (reportRef.current) {
        const containerWidth = reportRef.current.parentElement.clientWidth;
        const a4WidthPx = 794; // 96 DPI 기준 A4 너비 (210mm)
        if (containerWidth < a4WidthPx) {
          setScale(containerWidth / (a4WidthPx + 40)); // 여백 고려
        } else {
          setScale(1);
        }
      }
    };

    window.addEventListener('resize', updateScale);
    updateScale();
    return () => window.removeEventListener('resize', updateScale);
  }, []);

  return (
    <div className="preview-wrapper bg-slate-100 p-8 flex justify-center overflow-auto min-h-screen">
      {/* A4 용지 레이아웃 컨테이너 */}
      <div
        ref={reportRef}
        className="a4-page bg-white shadow-2xl p-12 relative flex flex-col justify-between"
        style={{
          transform: `scale(${scale})`,
          transformOrigin: 'top center',
        }}
      >
        {/* 헤더 */}
        <header className="border-b-2 border-teal-700 pb-4 mb-6">
          <div className="flex justify-between items-end">
            <h1 className="text-2xl font-bold text-slate-800 tracking-tight">
              사업 타당성 중간 보고서
            </h1>
            <span className="text-xs text-slate-500 font-medium">
              Project: {reportData?.projectName || '스마트 5구 안경 거치대'}
            </span>
          </div>
        </header>

        {/* 섹션 1: 검증 요약 */}
        <section className="mb-6">
          <h2 className="text-base font-bold text-teal-800 border-l-4 border-teal-600 pl-2 mb-2">
            1. 사업 개요 및 종합 평가
          </h2>
          <div className="bg-slate-50 border border-slate-200 rounded-lg p-4 grid grid-cols-3 gap-4 text-center">
            <div>
              <span className="text-xs text-slate-500 block">종합 스코어</span>
              <span className="text-xl font-extrabold text-teal-600">78 / 100</span>
            </div>
            <div>
              <span className="text-xs text-slate-500 block">검증 판정</span>
              <span className="text-sm font-bold text-amber-600 px-2 py-0.5 bg-amber-50 rounded border border-amber-200 inline-block mt-1">
                조건부 적합 (보완 필요)
              </span>
            </div>
            <div>
              <span className="text-xs text-slate-500 block">핵심 위험 요소</span>
              <span className="text-xs font-semibold text-slate-700 block mt-1">
                가격 저항선 초과 (35% 감지)
              </span>
            </div>
          </div>
        </section>

        {/* 섹션 2: 초기 기획 vs AI 검증 비교 (격자 레이아웃 + 글자 자동 조절) */}
        <section className="mb-6">
          <h2 className="text-base font-bold text-teal-800 border-l-4 border-teal-600 pl-2 mb-3">
            2. 초기 기획 vs AI 검증 비교 (Market & BM Gap)
          </h2>

          <div className="grid-table border border-slate-300 text-xs">
            {/* 표 헤더 */}
            <div className="grid grid-cols-12 bg-slate-100 font-bold border-b border-slate-300 text-slate-700 p-2 text-center">
              <div className="col-span-2">검증 영역</div>
              <div className="col-span-3">초기 사업계획서 (Input)</div>
              <div className="col-span-3">AI 에이전트 검증 (Output)</div>
              <div className="col-span-4">Gap 분석 및 인사이트</div>
            </div>

            {/* 행 1: 시장 규모 */}
            <div className="grid grid-cols-12 border-b border-slate-200 p-2.5 items-center hover:bg-slate-50">
              <div className="col-span-2 font-bold text-slate-700">시장 규모</div>
              <div className="col-span-3 text-slate-600 pr-1 auto-fit-text">
                국내 1조 원, 유효시장 2,000억 원 추정
              </div>
              <div className="col-span-3 font-semibold text-teal-700 pr-1 auto-fit-text">
                TAM: 8,500억 / SAM: 1,200억 / SOM: 150억
              </div>
              <div className="col-span-4 text-slate-600 bg-amber-50/50 p-1.5 rounded border border-amber-100 auto-fit-text">
                초기 추정 대비 유효 시장이 40% 과대평가됨. SOM 목표 조정 필요.
              </div>
            </div>

            {/* 행 2: 가격 및 수익 구조 */}
            <div className="grid grid-cols-12 p-2.5 items-center hover:bg-slate-50">
              <div className="col-span-2 font-bold text-slate-700">가격 정책</div>
              <div className="col-span-3 text-slate-600 pr-1 auto-fit-text">
                월 정기 구독료 15,000원 희망
              </div>
              <div className="col-span-3 font-semibold text-teal-700 pr-1 auto-fit-text">
                권장가: 9,900원 ~ 12,900원 (PSM 분석)
              </div>
              <div className="col-span-4 text-slate-600 bg-rose-50/50 p-1.5 rounded border border-rose-100 auto-fit-text">
                희망가 설정 시 가격 저항선 초과로 구매 의향도 35% 급감.
              </div>
            </div>
          </div>
        </section>

        {/* 섹션 3: 재무 리포트 요약 */}
        <section className="mb-6">
          <h2 className="text-base font-bold text-teal-800 border-l-4 border-teal-600 pl-2 mb-2">
            3. 정밀 재무 및 수익성 검토
          </h2>
          <div className="grid grid-cols-3 gap-3 text-xs">
            <div className="p-3 border rounded border-slate-200">
              <span className="text-slate-500 font-medium block">손익분기점 (BEP)</span>
              <span className="font-bold text-slate-800 text-sm">14개월 차 (2,800명)</span>
            </div>
            <div className="p-3 border rounded border-slate-200">
              <span className="text-slate-500 font-medium block">순현가 (NPV)</span>
              <span className="font-bold text-slate-800 text-sm">1.8억 원 (할인율 10%)</span>
            </div>
            <div className="p-3 border rounded border-slate-200">
              <span className="text-slate-500 font-medium block">내부수익률 (IRR)</span>
              <span className="font-bold text-teal-700 text-sm">18.5% (사업성 보유)</span>
            </div>
          </div>
        </section>

        {/* 푸터 */}
        <footer className="border-t border-slate-200 pt-3 text-between flex justify-between text-[10px] text-slate-400">
          <span>AI 사업검증 플랫폼 (AI Business Validation Platform)</span>
          <span>Page 1 of 1</span>
        </footer>
      </div>
    </div>
  );
};
