import React, {
  useEffect,
  useRef,
  useState,
} from 'react';
import { useNavigate } from 'react-router-dom';
import './VirtualMarket.css';

/* =========================================================
   데모용 DB 상품 데이터
   추후 API 응답 데이터로 교체
========================================================= */

const MOCK_PRODUCTS = [
  {
    id: 1,
    name: '바삭 프로틴 크런치',
    category: '건강 간식',
    price: 3500,
    targetCustomer: '건강 간식을 찾는 20대 직장인',
    summary:
      '바삭한 토핑과 부드러운 크림을 결합한 저당 단백질 스낵',
    features: ['저당', '고단백', '바삭한 식감'],
  },
  {
    id: 2,
    name: '데일리 밸런스 바',
    category: '에너지바',
    price: 2900,
    targetCustomer: '대학생 및 취업 준비생',
    summary:
      '간편하게 섭취할 수 있는 합리적인 가격의 에너지바',
    features: ['간편성', '가성비', '휴대성'],
  },
  {
    id: 3,
    name: '프리미엄 너츠 크림',
    category: '디저트',
    price: 4200,
    targetCustomer: '품질과 풍미를 중시하는 30대 소비자',
    summary:
      '견과류 풍미와 부드러운 크림을 강조한 프리미엄 디저트',
    features: ['프리미엄', '견과류', '선물용'],
  },
  {
    id: 4,
    name: '라이트 요거트 큐브',
    category: '저당 디저트',
    price: 3200,
    targetCustomer: '가벼운 디저트를 선호하는 소비자',
    summary:
      '요거트 풍미와 가벼운 식감을 결합한 한입 디저트',
    features: ['저칼로리', '상큼함', '한입 크기'],
  },
];

/* =========================================================
   가상 시장 검증 하위 섹션
========================================================= */

const VIRTUAL_MARKET_SECTION = {
  OVERVIEW: 'overview',
  PANEL_REACTION: 'panel-reaction',
  MARKET_PREDICTION: 'market-prediction',
  MARKETING_CONTENT: 'marketing-content',
};

/* =========================================================
   마케팅 콘텐츠 제작 내부 단계
========================================================= */

const MARKETING_STEP = {
  FORM: 'form',
  GENERATING: 'generating',
  RESULT: 'result',
};

/* =========================================================
   상단 하위 기능 탭
========================================================= */

const VIRTUAL_MARKET_TABS = [
  {
    id: VIRTUAL_MARKET_SECTION.PANEL_REACTION,
    label: 'AI 패널 반응 조사',
  },
  {
    id: VIRTUAL_MARKET_SECTION.MARKET_PREDICTION,
    label: '시장 반응 예측',
  },
  {
    id: VIRTUAL_MARKET_SECTION.MARKETING_CONTENT,
    label: '마케팅 콘텐츠 제작',
  },
];

/* =========================================================
   최상위 가상 시장 검증 페이지
========================================================= */

function VirtualMarket() {
  const navigate = useNavigate();

  const generationTimerRef = useRef(null);
  const resultDelayRef = useRef(null);

  const [activeSection, setActiveSection] = useState(
    VIRTUAL_MARKET_SECTION.OVERVIEW,
  );

  const [marketingStep, setMarketingStep] = useState(
    MARKETING_STEP.FORM,
  );

  const [progress, setProgress] = useState(0);

  const [products] = useState(MOCK_PRODUCTS);

  const [selectedProductId, setSelectedProductId] =
    useState(null);

  const [uploadedImage, setUploadedImage] =
    useState(null);

  const [imagePreview, setImagePreview] =
    useState('');

  const [marketingFormData, setMarketingFormData] =
    useState({
      promotionName: '',
      mainBannerText: '',
      subText: '',
      bannerTone: '신뢰감 있는',
      bannerSize: '가로형 배너',
      keywords: '',
    });

  const [generatedBannerData, setGeneratedBannerData] =
    useState({
      productName: '',
      headline: '',
      subText: '',
    });

  const selectedProduct =
    products.find(
      (product) => product.id === selectedProductId,
    ) ?? null;

  /* =======================================================
     타이머 정리
  ======================================================= */

  const clearGenerationTimers = () => {
    if (generationTimerRef.current) {
      clearInterval(generationTimerRef.current);
      generationTimerRef.current = null;
    }

    if (resultDelayRef.current) {
      clearTimeout(resultDelayRef.current);
      resultDelayRef.current = null;
    }
  };

  useEffect(() => {
    return () => {
      clearGenerationTimers();
    };
  }, []);

  useEffect(() => {
    return () => {
      if (imagePreview) {
        URL.revokeObjectURL(imagePreview);
      }
    };
  }, [imagePreview]);

  /* =======================================================
     섹션 전환
  ======================================================= */

  const handleSectionChange = (sectionId) => {
    if (sectionId === activeSection) {
      return;
    }

    if (
      activeSection ===
        VIRTUAL_MARKET_SECTION.MARKETING_CONTENT &&
      marketingStep === MARKETING_STEP.GENERATING
    ) {
      clearGenerationTimers();
      setProgress(0);
      setMarketingStep(MARKETING_STEP.FORM);
    }

    setActiveSection(sectionId);
  };

  const handleReturnToOverview = () => {
    clearGenerationTimers();
    setProgress(0);
    setMarketingStep(MARKETING_STEP.FORM);
    setActiveSection(
      VIRTUAL_MARKET_SECTION.OVERVIEW,
    );
  };

  /* =======================================================
     마케팅 입력값 처리
  ======================================================= */

  const handleMarketingInputChange = (event) => {
    const { name, value } = event.target;

    setMarketingFormData((previous) => ({
      ...previous,
      [name]: value,
    }));
  };

  /* =======================================================
     이미지 업로드 처리
  ======================================================= */

  const handleImageChange = (event) => {
    const file = event.target.files?.[0];

    if (!file) {
      return;
    }

    const allowedTypes = [
      'image/png',
      'image/jpeg',
    ];

    if (!allowedTypes.includes(file.type)) {
      window.alert(
        'PNG 또는 JPG 이미지만 업로드할 수 있습니다.',
      );

      event.target.value = '';
      return;
    }

    if (file.size > 10 * 1024 * 1024) {
      window.alert(
        '이미지 파일은 최대 10MB까지 업로드할 수 있습니다.',
      );

      event.target.value = '';
      return;
    }

    const previewUrl = URL.createObjectURL(file);

    setUploadedImage(file);
    setImagePreview(previewUrl);
  };

  const handleRemoveImage = () => {
    setUploadedImage(null);
    setImagePreview('');
  };

  /* =======================================================
     광고 배너 생성 데모
  ======================================================= */

  const handleGenerateBanner = () => {
    if (
      !marketingFormData.promotionName.trim()
    ) {
      window.alert(
        '프로모션 이름을 입력해 주세요.',
      );
      return;
    }

    if (
      !marketingFormData.mainBannerText.trim()
    ) {
      window.alert(
        '메인 배너 문구를 입력해 주세요.',
      );
      return;
    }

    if (!selectedProduct) {
      window.alert(
        '광고에 사용할 상품을 선택해 주세요.',
      );
      return;
    }

    clearGenerationTimers();

    setProgress(0);
    setMarketingStep(
      MARKETING_STEP.GENERATING,
    );

    generationTimerRef.current =
      window.setInterval(() => {
        setProgress((previous) => {
          const nextProgress = previous + 7;

          if (nextProgress >= 100) {
            clearGenerationTimers();

            setGeneratedBannerData({
              productName: selectedProduct.name,

              headline:
                marketingFormData.mainBannerText.trim(),

              subText:
                marketingFormData.subText.trim() ||
                marketingFormData.keywords.trim() ||
                selectedProduct.summary,
            });

            resultDelayRef.current =
              window.setTimeout(() => {
                setMarketingStep(
                  MARKETING_STEP.RESULT,
                );

                resultDelayRef.current = null;
              }, 350);

            return 100;
          }

          return nextProgress;
        });
      }, 180);
  };

  const handleCancelGeneration = () => {
    clearGenerationTimers();
    setProgress(0);
    setMarketingStep(MARKETING_STEP.FORM);
  };

  const handleRestartMarketing = () => {
    clearGenerationTimers();
    setProgress(0);
    setMarketingStep(MARKETING_STEP.FORM);
  };

  /* =======================================================
     Canvas 배너 이미지 저장
  ======================================================= */

  const loadPreviewImage = () => {
    return new Promise((resolve, reject) => {
      if (!imagePreview) {
        resolve(null);
        return;
      }

      const image = new Image();

      image.onload = () => resolve(image);

      image.onerror = () =>
        reject(
          new Error(
            '이미지를 불러오지 못했습니다.',
          ),
        );

      image.src = imagePreview;
    });
  };

  const drawCoverImage = (
    context,
    image,
    x,
    y,
    width,
    height,
  ) => {
    const imageRatio =
      image.width / image.height;

    const boxRatio = width / height;

    let sourceX = 0;
    let sourceY = 0;
    let sourceWidth = image.width;
    let sourceHeight = image.height;

    if (imageRatio > boxRatio) {
      sourceWidth =
        image.height * boxRatio;

      sourceX =
        (image.width - sourceWidth) / 2;
    } else {
      sourceHeight =
        image.width / boxRatio;

      sourceY =
        (image.height - sourceHeight) / 2;
    }

    context.drawImage(
      image,
      sourceX,
      sourceY,
      sourceWidth,
      sourceHeight,
      x,
      y,
      width,
      height,
    );
  };

  const drawWrappedText = (
    context,
    text,
    centerX,
    startY,
    maxWidth,
    lineHeight,
  ) => {
    const characters = [...text];
    const lines = [];

    let line = '';

    characters.forEach((character) => {
      const testLine = `${line}${character}`;

      const width =
        context.measureText(testLine).width;

      if (width > maxWidth && line) {
        lines.push(line);
        line = character;
      } else {
        line = testLine;
      }
    });

    if (line) {
      lines.push(line);
    }

    lines
      .slice(0, 3)
      .forEach((currentLine, index) => {
        context.fillText(
          currentLine,
          centerX,
          startY + index * lineHeight,
        );
      });
  };

  const handleSaveBanner = async () => {
    try {
      const canvas =
        document.createElement('canvas');

      canvas.width = 1200;
      canvas.height = 628;

      const context =
        canvas.getContext('2d');

      if (!context) {
        throw new Error(
          'Canvas를 사용할 수 없습니다.',
        );
      }

      context.fillStyle = '#dff5ed';

      context.fillRect(
        0,
        0,
        canvas.width,
        canvas.height,
      );

      if (imagePreview) {
        const image = await loadPreviewImage();

        if (image) {
          context.save();

          context.beginPath();

          context.roundRect(
            70,
            94,
            330,
            440,
            24,
          );

          context.clip();

          drawCoverImage(
            context,
            image,
            70,
            94,
            330,
            440,
          );

          context.restore();
        }
      }

      const contentCenterX =
        imagePreview ? 790 : 600;

      const contentWidth =
        imagePreview ? 660 : 980;

      context.textAlign = 'center';

      context.fillStyle = '#2f6f5b';

      context.font =
        '700 26px -apple-system, BlinkMacSystemFont, "Segoe UI", sans-serif';

      context.fillText(
        marketingFormData.promotionName ||
          '프로모션',
        contentCenterX,
        125,
      );

      context.fillStyle = '#36715c';

      context.font =
        '700 30px -apple-system, BlinkMacSystemFont, "Segoe UI", sans-serif';

      context.fillText(
        generatedBannerData.productName ||
          '생성된 광고 배너',
        contentCenterX,
        185,
      );

      context.fillStyle = '#17251f';

      context.font =
        '700 52px -apple-system, BlinkMacSystemFont, "Segoe UI", sans-serif';

      drawWrappedText(
        context,
        generatedBannerData.headline,
        contentCenterX,
        270,
        contentWidth,
        65,
      );

      context.fillStyle = '#52655e';

      context.font =
        '400 28px -apple-system, BlinkMacSystemFont, "Segoe UI", sans-serif';

      drawWrappedText(
        context,
        generatedBannerData.subText,
        contentCenterX,
        430,
        contentWidth,
        40,
      );

      context.fillStyle = '#6cc2a5';

      context.beginPath();

      context.roundRect(
        contentCenterX - 150,
        510,
        300,
        64,
        32,
      );

      context.fill();

      context.fillStyle = '#153a2e';

      context.font =
        '700 25px -apple-system, BlinkMacSystemFont, "Segoe UI", sans-serif';

      context.fillText(
        '자세히 알아보기',
        contentCenterX,
        551,
      );

      const downloadLink =
        document.createElement('a');

      const safeFileName =
        marketingFormData.promotionName
          .trim()
          .replace(/[\\/:*?"<>|]/g, '_') ||
        'marketing';

      downloadLink.download =
        `${safeFileName}-banner.png`;

      downloadLink.href =
        canvas.toDataURL('image/png');

      downloadLink.click();
    } catch (error) {
      console.error(error);

      window.alert(
        '광고 배너를 저장하지 못했습니다.',
      );
    }
  };

  return (
    <div className="virtual-market-page">
      <aside className="virtual-market-sidebar">
        <button
          type="button"
          className="virtual-market-home-button"
          onClick={() => navigate('/')}
          aria-label="홈으로 이동"
        >
          ⌂
        </button>

        <nav
          className="virtual-market-side-menu"
          aria-label="사용자 메뉴"
        >
          <button
            type="button"
            onClick={() =>
              navigate('/business-analysis')
            }
          >
            사업성 분석
          </button>

          <button
            type="button"
            onClick={() =>
              navigate('/project-create')
            }
          >
            기획서 등록
          </button>

          <button
            type="button"
            className="active"
            onClick={handleReturnToOverview}
          >
            가상 시장 검증
          </button>

          <button
            type="button"
            onClick={() =>
              navigate('/dashboard')
            }
          >
            대시보드
          </button>
        </nav>
      </aside>

      <main className="virtual-market-main">
        <div
          className="virtual-market-tabs"
          aria-label="가상 시장 검증 기능"
        >
          {VIRTUAL_MARKET_TABS.map(
            (tab) => (
              <button
                type="button"
                key={tab.id}
                className={
                  activeSection === tab.id
                    ? 'virtual-market-tab active'
                    : 'virtual-market-tab'
                }
                onClick={() =>
                  handleSectionChange(tab.id)
                }
              >
                {tab.label}
              </button>
            ),
          )}
        </div>

        {activeSection ===
          VIRTUAL_MARKET_SECTION.OVERVIEW && (
          <VirtualMarketOverview
            onStart={() =>
              handleSectionChange(
                VIRTUAL_MARKET_SECTION.PANEL_REACTION,
              )
            }
            onSelectSection={
              handleSectionChange
            }
          />
        )}

        {activeSection ===
          VIRTUAL_MARKET_SECTION.PANEL_REACTION && (
          <PanelReactionPreview
            onStart={() =>
              window.alert(
                'AI 패널 반응 조사가 시작되었습니다.',
              )
            }
          />
        )}

        {activeSection ===
          VIRTUAL_MARKET_SECTION.MARKET_PREDICTION && (
          <MarketPredictionPreview
            onStart={() =>
              window.alert(
                '시장 반응 예측이 시작되었습니다.',
              )
            }
          />
        )}

        {activeSection ===
          VIRTUAL_MARKET_SECTION.MARKETING_CONTENT &&
          marketingStep ===
            MARKETING_STEP.FORM && (
            <MarketingContentForm
              formData={marketingFormData}
              onChange={
                handleMarketingInputChange
              }
              onGenerate={
                handleGenerateBanner
              }
              products={products}
              selectedProductId={
                selectedProductId
              }
              onSelectProduct={
                setSelectedProductId
              }
              uploadedImage={uploadedImage}
              imagePreview={imagePreview}
              onImageChange={
                handleImageChange
              }
              onRemoveImage={
                handleRemoveImage
              }
            />
          )}

        {activeSection ===
          VIRTUAL_MARKET_SECTION.MARKETING_CONTENT &&
          marketingStep ===
            MARKETING_STEP.GENERATING && (
            <MarketingContentGenerating
              progress={progress}
              onCancel={
                handleCancelGeneration
              }
            />
          )}

        {activeSection ===
          VIRTUAL_MARKET_SECTION.MARKETING_CONTENT &&
          marketingStep ===
            MARKETING_STEP.RESULT && (
            <MarketingContentResult
              generatedData={
                generatedBannerData
              }
              formData={marketingFormData}
              selectedProduct={
                selectedProduct
              }
              imagePreview={imagePreview}
              onSave={handleSaveBanner}
              onRestart={
                handleRestartMarketing
              }
            />
          )}
      </main>
    </div>
  );
}

/* =========================================================
   가상 시장 검증 진입 화면
========================================================= */

function VirtualMarketOverview({
  onStart,
  onSelectSection,
}) {
  return (
    <section className="virtual-overview">
      <div className="virtual-overview-content">
        <span className="virtual-overview-label">
          가상 시장 검증
        </span>

        <h1>가상 시장 검증</h1>

        <p>
          AI 페르소나를 활용한 시뮬레이션을 통해
          <br />
          시장 반응을 예측하고 구매 의향,
          적정 가격,
          <br />
          주요 의견을 분석합니다.
        </p>

        <div className="virtual-overview-features">
          <button
            type="button"
            onClick={() =>
              onSelectSection(
                VIRTUAL_MARKET_SECTION.PANEL_REACTION,
              )
            }
          >
            <strong>
              AI 패널 반응 조사
            </strong>

            <span>
              가상 고객 패널의 개별 의견과
              반응을 확인합니다.
            </span>
          </button>

          <button
            type="button"
            onClick={() =>
              onSelectSection(
                VIRTUAL_MARKET_SECTION.MARKET_PREDICTION,
              )
            }
          >
            <strong>
              시장 반응 예측
            </strong>

            <span>
              구매 의향과 가격 수용도 등
              시장 반응을 예측합니다.
            </span>
          </button>

          <button
            type="button"
            onClick={() =>
              onSelectSection(
                VIRTUAL_MARKET_SECTION.MARKETING_CONTENT,
              )
            }
          >
            <strong>
              마케팅 콘텐츠 제작
            </strong>

            <span>
              검증된 상품 정보를 활용하여
              광고 배너를 제작합니다.
            </span>
          </button>
        </div>

        <div className="virtual-overview-actions">
          <button
            type="button"
            className="marketing-primary-button"
            onClick={onStart}
          >
            시작하기
          </button>
        </div>
      </div>
    </section>
  );
}

/* =========================================================
   AI 패널 반응 조사 프리뷰
========================================================= */

function PanelReactionPreview({
  onStart,
}) {
  const panelExamples = [
    {
      id: 1,
      name: '가격 민감형 소비자',
      target: '20대 대학생',
      response:
        '상품의 특징은 매력적이지만 3,500원은 조금 부담스럽습니다.',
      score: 62,
    },
    {
      id: 2,
      name: '품질 우선형 소비자',
      target: '20~30대 직장인',
      response:
        '저당과 고단백보다 맛과 식감이 명확하게 전달되면 구매할 의향이 있습니다.',
      score: 81,
    },
    {
      id: 3,
      name: '신제품 탐색형 소비자',
      target: '디지털 친화 소비자',
      response:
        '새로운 식감과 패키지가 강조된다면 한 번 체험해 보고 싶습니다.',
      score: 76,
    },
  ];

  return (
    <section className="virtual-preview-section">
      <header className="virtual-preview-header">
        <div>
          <span className="virtual-section-chip">
            AI 패널 반응 조사
          </span>

          <h1>
            가상 고객 패널 반응 조사
          </h1>

          <p>
            다양한 소비자 페르소나가 상품과
            서비스에 대해 어떤 반응을 보이는지
            미리 확인합니다.
          </p>
        </div>

        <button
          type="button"
          className="marketing-primary-button"
          onClick={onStart}
        >
          반응 조사 시작
        </button>
      </header>

      <div className="reaction-summary-grid">
        <div>
          <span>참여 패널</span>
          <strong>100명</strong>
        </div>

        <div>
          <span>평균 구매 의향</span>
          <strong>73%</strong>
        </div>

        <div>
          <span>긍정 반응</span>
          <strong>68명</strong>
        </div>

        <div>
          <span>주요 이슈</span>
          <strong>가격</strong>
        </div>
      </div>

      <div className="reaction-preview-list">
        {panelExamples.map((panel) => (
          <article
            className="reaction-preview-card"
            key={panel.id}
          >
            <div className="reaction-preview-top">
              <div className="reaction-preview-avatar">
                {panel.name.slice(0, 1)}
              </div>

              <div>
                <h2>{panel.name}</h2>
                <span>{panel.target}</span>
              </div>

              <strong>
                {panel.score}%
              </strong>
            </div>

            <p>{panel.response}</p>

            <div className="reaction-score-track">
              <div
                style={{
                  width: `${panel.score}%`,
                }}
              />
            </div>
          </article>
        ))}
      </div>

      <div className="virtual-preview-note">
        현재 화면은 데모용 프리뷰입니다.
        실제 조사 실행 시 프로젝트에 설정된
        페르소나와 검증 질문을 기준으로 결과가
        생성됩니다.
      </div>
    </section>
  );
}

/* =========================================================
   시장 반응 예측 프리뷰
========================================================= */

function MarketPredictionPreview({
  onStart,
}) {
  const predictions = [
    {
      id: 1,
      label: '구매 의향',
      value: 72,
      description:
        '목표 고객 중 구매를 긍정적으로 고려할 것으로 예상되는 비율',
    },
    {
      id: 2,
      label: '가격 수용도',
      value: 58,
      description:
        '현재 제안 가격을 적절하다고 판단할 것으로 예상되는 비율',
    },
    {
      id: 3,
      label: '재구매 가능성',
      value: 64,
      description:
        '초기 구매 후 반복 구매로 이어질 것으로 예상되는 비율',
    },
  ];

  return (
    <section className="virtual-preview-section">
      <header className="virtual-preview-header">
        <div>
          <span className="virtual-section-chip">
            시장 반응 예측
          </span>

          <h1>시장 반응 예측</h1>

          <p>
            AI 패널의 반응과 상품 정보를
            종합하여 구매 가능성, 가격 수용도,
            주요 위험 요인을 예측합니다.
          </p>
        </div>

        <button
          type="button"
          className="marketing-primary-button"
          onClick={onStart}
        >
          시장 예측 시작
        </button>
      </header>

      <div className="prediction-main-grid">
        <article className="prediction-score-card">
          <span>
            종합 시장 반응 점수
          </span>

          <div className="prediction-main-score">
            71
            <small>/ 100</small>
          </div>

          <strong>부분 적합</strong>

          <p>
            상품 매력도는 높지만 가격과
            포지셔닝에 대한 보완이 필요한
            것으로 예측됩니다.
          </p>
        </article>

        <div className="prediction-metric-list">
          {predictions.map(
            (prediction) => (
              <article
                className="prediction-metric-card"
                key={prediction.id}
              >
                <div className="prediction-metric-header">
                  <strong>
                    {prediction.label}
                  </strong>

                  <span>
                    {prediction.value}%
                  </span>
                </div>

                <div className="prediction-progress-track">
                  <div
                    style={{
                      width:
                        `${prediction.value}%`,
                    }}
                  />
                </div>

                <p>
                  {prediction.description}
                </p>
              </article>
            ),
          )}
        </div>
      </div>

      <div className="prediction-insight-grid">
        <article>
          <span>긍정 요인</span>

          <strong>
            맛과 식감에 대한 기대
          </strong>

          <p>
            바삭한 식감과 간편한 섭취 방식이
            핵심 매력으로 평가될 가능성이
            높습니다.
          </p>
        </article>

        <article>
          <span>위험 요인</span>

          <strong>가격 부담</strong>

          <p>
            일부 가격 민감 고객군에서는 현재
            가격이 구매 저해 요인이 될 수
            있습니다.
          </p>
        </article>

        <article>
          <span>추천 방향</span>

          <strong>
            맛 중심 포지셔닝
          </strong>

          <p>
            영양 기능보다 맛과 식감을 먼저
            전달하는 메시지가 더 효과적일 수
            있습니다.
          </p>
        </article>
      </div>

      <div className="virtual-preview-note">
        현재 수치는 실제 분석 결과가 아닌
        UI 데모용 예시 데이터입니다.
      </div>
    </section>
  );
}

/* =========================================================
   마케팅 콘텐츠 입력 화면
========================================================= */

function MarketingContentForm({
  formData,
  onChange,
  onGenerate,
  products,
  selectedProductId,
  onSelectProduct,
  uploadedImage,
  imagePreview,
  onImageChange,
  onRemoveImage,
}) {
  return (
    <section className="marketing-form-section">
      <header className="marketing-section-header">
        <span className="virtual-section-chip">
          마케팅 콘텐츠 제작
        </span>

        <h1>AI 광고 배너 생성</h1>

        <p>
          등록된 상품을 선택하고 프로모션
          내용을 입력하면 AI 광고 배너를
          생성합니다.
        </p>
      </header>

      <div className="marketing-form-card">
        <label className="marketing-field">
          <span>
            프로모션 이름
            <strong>*</strong>
          </span>

          <input
            type="text"
            name="promotionName"
            value={formData.promotionName}
            onChange={onChange}
            placeholder="예: 여름맞이 신제품 출시 프로모션"
          />
        </label>

        <label className="marketing-field">
          <span>
            메인 배너
            <strong>*</strong>
          </span>

          <textarea
            name="mainBannerText"
            value={formData.mainBannerText}
            onChange={onChange}
            rows={5}
            placeholder="메인 배너에 들어갈 광고 문구를 입력하세요."
          />
        </label>

        <label className="marketing-field">
          <span>보조 문구</span>

          <input
            type="text"
            name="subText"
            value={formData.subText}
            onChange={onChange}
            placeholder="예: 맛과 건강을 한 번에"
          />
        </label>

        <div className="marketing-source-grid">
          <MarketingImageUpload
            uploadedImage={uploadedImage}
            imagePreview={imagePreview}
            onImageChange={onImageChange}
            onRemoveImage={onRemoveImage}
          />

          <MarketingProductSelect
            products={products}
            selectedProductId={
              selectedProductId
            }
            onSelectProduct={
              onSelectProduct
            }
          />
        </div>

        <div className="marketing-form-row">
          <label className="marketing-field">
            <span>광고 분위기</span>

            <select
              name="bannerTone"
              value={formData.bannerTone}
              onChange={onChange}
            >
              <option value="신뢰감 있는">
                신뢰감 있는
              </option>

              <option value="밝고 친근한">
                밝고 친근한
              </option>

              <option value="감성적인">
                감성적인
              </option>

              <option value="전문적인">
                전문적인
              </option>

              <option value="강렬한">
                강렬한
              </option>

              <option value="고급스러운">
                고급스러운
              </option>

              <option value="미니멀한">
                미니멀한
              </option>
            </select>
          </label>

          <label className="marketing-field">
            <span>배너 형식</span>

            <select
              name="bannerSize"
              value={formData.bannerSize}
              onChange={onChange}
            >
              <option value="가로형 배너">
                가로형 배너
              </option>

              <option value="정사각형 SNS 광고">
                정사각형 SNS 광고
              </option>

              <option value="세로형 모바일 광고">
                세로형 모바일 광고
              </option>
            </select>
          </label>
        </div>

        <label className="marketing-field">
          <span>강조 키워드</span>

          <input
            type="text"
            name="keywords"
            value={formData.keywords}
            onChange={onChange}
            placeholder="예: 저당, 바삭한 식감, 신제품"
          />
        </label>

        <div className="marketing-form-actions">
          <button
            type="button"
            className="marketing-cancel-button"
            onClick={() =>
              window.history.back()
            }
          >
            취소
          </button>

          <button
            type="button"
            className="marketing-primary-button"
            onClick={onGenerate}
          >
            광고 배너 생성하기
          </button>
        </div>
      </div>
    </section>
  );
}

/* =========================================================
   이미지 업로드
========================================================= */

function MarketingImageUpload({
  uploadedImage,
  imagePreview,
  onImageChange,
  onRemoveImage,
}) {
  const fileInputRef = useRef(null);

  const handleOpenFilePicker = () => {
    fileInputRef.current?.click();
  };

  return (
    <div className="marketing-source-section">
      <div className="marketing-source-title">
        <span>이미지 업로드</span>

        <button
          type="button"
          className="marketing-upload-trigger"
          onClick={handleOpenFilePicker}
          aria-label="광고 이미지 선택"
        >
          ↑
        </button>
      </div>

      <button
        type="button"
        className="marketing-image-upload-box"
        onClick={handleOpenFilePicker}
      >
        {imagePreview ? (
          <img
            src={imagePreview}
            alt="업로드한 광고 이미지 미리보기"
          />
        ) : (
          <span>
            PNG, JPG 파일 지원
            <br />
            최대 10MB
          </span>
        )}
      </button>

      {uploadedImage && (
        <div className="marketing-upload-file-info">
          <span title={uploadedImage.name}>
            {uploadedImage.name}
          </span>

          <button
            type="button"
            onClick={onRemoveImage}
          >
            삭제
          </button>
        </div>
      )}

      <input
        ref={fileInputRef}
        type="file"
        accept="image/png,image/jpeg"
        hidden
        onChange={onImageChange}
      />
    </div>
  );
}

/* =========================================================
   상품 선택
========================================================= */

function MarketingProductSelect({
  products,
  selectedProductId,
  onSelectProduct,
}) {
  return (
    <div className="marketing-source-section">
      <div className="marketing-source-title">
        <span>상품 선택</span>

        <small>
          등록된 상품 {products.length}개
        </small>
      </div>

      <div className="marketing-product-list">
        {products.map((product) => {
          const selected =
            selectedProductId === product.id;

          return (
            <button
              type="button"
              key={product.id}
              className={
                selected
                  ? 'marketing-product-card selected'
                  : 'marketing-product-card'
              }
              onClick={() =>
                onSelectProduct(product.id)
              }
            >
              <div className="marketing-product-card-header">
                <strong>
                  {product.name}
                </strong>

                <span>
                  {product.price.toLocaleString()}
                  원
                </span>
              </div>

              <span className="marketing-product-category">
                {product.category}
              </span>

              <p>{product.summary}</p>

              <div className="marketing-product-features">
                {product.features.map(
                  (feature) => (
                    <span key={feature}>
                      {feature}
                    </span>
                  ),
                )}
              </div>

              {selected && (
                <span className="marketing-selected-label">
                  선택됨
                </span>
              )}
            </button>
          );
        })}
      </div>
    </div>
  );
}

/* =========================================================
   마케팅 생성 진행 화면
========================================================= */

function MarketingContentGenerating({
  progress,
  onCancel,
}) {
  return (
    <section className="marketing-loading-section">
      <span className="virtual-section-chip">
        마케팅 콘텐츠 제작
      </span>

      <div className="marketing-loading-content">
        <div
          className="marketing-progress-circle"
          style={{
            background: `conic-gradient(
              #63c5a3 ${progress * 3.6}deg,
              #e5ebe8 ${progress * 3.6}deg
            )`,
          }}
        >
          <div className="marketing-progress-inner">
            <strong>{progress}%</strong>
          </div>
        </div>

        <h1>
          AI가 광고 배너를 생성하고 있습니다
        </h1>

        <p>
          선택한 상품 정보와 프로모션 내용을
          분석하여 광고 문구와 배너 시안을
          제작하고 있습니다.
        </p>

        <div
          className="marketing-loading-status"
          aria-live="polite"
        >
          {progress < 30 &&
            '선택한 상품 정보를 분석하고 있습니다.'}

          {progress >= 30 &&
            progress < 60 &&
            '프로모션 문구와 광고 스타일을 구성하고 있습니다.'}

          {progress >= 60 &&
            progress < 90 &&
            '업로드 이미지와 배너 레이아웃을 조합하고 있습니다.'}

          {progress >= 90 &&
            '최종 광고 배너를 정리하고 있습니다.'}
        </div>

        <button
          type="button"
          className="marketing-cancel-button"
          onClick={onCancel}
        >
          생성 취소
        </button>
      </div>
    </section>
  );
}

/* =========================================================
   마케팅 결과 화면
========================================================= */

function MarketingContentResult({
  generatedData,
  formData,
  selectedProduct,
  imagePreview,
  onSave,
  onRestart,
}) {
  return (
    <section className="marketing-result-section">
      <div className="marketing-result-header">
        <div>
          <span className="virtual-section-chip">
            마케팅 콘텐츠 제작
          </span>

          <h1>생성된 광고 배너</h1>

          <p>
            생성된 문구와 배너 시안을 확인하고
            저장할 수 있습니다.
          </p>
        </div>

        <button
          type="button"
          className="marketing-secondary-button"
          onClick={onRestart}
        >
          다시 만들기
        </button>
      </div>

      <div
        className={
          imagePreview
            ? 'marketing-banner-preview has-image'
            : 'marketing-banner-preview'
        }
      >
        {imagePreview && (
          <div className="marketing-banner-image-wrap">
            <img
              className="marketing-banner-image"
              src={imagePreview}
              alt="광고 배너 상품 이미지"
            />
          </div>
        )}

        <div className="marketing-banner-content">
          <span className="marketing-banner-category">
            {formData.bannerTone} 광고
          </span>

          <span className="marketing-banner-promotion">
            {formData.promotionName}
          </span>

          <strong className="marketing-banner-product">
            {generatedData.productName}
          </strong>

          <h2>
            {generatedData.headline}
          </h2>

          <p>
            {generatedData.subText}
          </p>

          <button type="button">
            자세히 알아보기
          </button>
        </div>
      </div>

      <div className="marketing-result-info">
        <div>
          <span>선택 상품</span>

          <strong>
            {selectedProduct?.name || '-'}
          </strong>
        </div>

        <div>
          <span>상품 타깃</span>

          <strong>
            {selectedProduct?.targetCustomer ||
              '-'}
          </strong>
        </div>

        <div>
          <span>광고 분위기</span>

          <strong>
            {formData.bannerTone}
          </strong>
        </div>
      </div>

      <div className="marketing-result-actions">
        <button
          type="button"
          className="marketing-primary-button"
          onClick={onSave}
        >
          광고 배너 저장
        </button>
      </div>
    </section>
  );
}

export default VirtualMarket;