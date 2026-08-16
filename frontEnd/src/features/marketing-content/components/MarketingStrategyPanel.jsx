const SOURCE_LABELS = Object.freeze({
  SELECTED_CONCEPT: '선택 컨셉',
  LEGAL: '법률 검토',
  MARKET: '시장분석',
  BUSINESS_MODEL: 'BM 분석',
  TECH_OPS: '기술·운영',
  FINANCE: '재무 입력',
  FINANCE_REPORT: '재무 분석 보고서',
  TWIN_SURVEY: '패널 트윈 조사',
});

const SOURCE_ORDER = Object.keys(
  SOURCE_LABELS,
);

function StrategyList({
  title,
  values = [],
}) {
  if (!values.length) {
    return null;
  }

  return (
    <section className="mk-strategy__section">
      <h3>{title}</h3>
      <ul>
        {values.map((value) => (
          <li key={value}>{value}</li>
        ))}
      </ul>
    </section>
  );
}

export default function MarketingStrategyPanel({
  strategy,
  onNext,
}) {
  const view = strategy.view;
  const result = view?.result;

  const availableTypes = new Set(
    (view?.sourceManifest ?? [])
      .map((item) => item.type),
  );

  return (
    <div className="mk-strategy">
      <section className="mk-strategy__sources">
        <header>
          <div>
            <p>전략 입력 자료</p>
            <h3>분석 결과 연결 상태</h3>
          </div>
        </header>

        <ul>
          {SOURCE_ORDER.map((type) => {
            const available =
              availableTypes.has(type);

            return (
              <li
                key={type}
                data-ready={available}
              >
                <span>
                  {available ? '✓' : '!'}
                </span>

                <strong>
                  {SOURCE_LABELS[type]}
                </strong>

                <small>
                  {available
                    ? '연결 완료'
                    : '결과 필요'}
                </small>
              </li>
            );
          })}
        </ul>
      </section>

      {!view?.ready && (
        <div
          className="mk-alert mk-alert--danger"
          role="alert"
        >
          <strong>
            전략 생성에 필요한 분석 결과가
            부족합니다.
          </strong>

          <p>
            {(view?.missingSources ?? [])
              .map((type) =>
                SOURCE_LABELS[type] ?? type)
              .join(' · ')}
          </p>
        </div>
      )}

      {strategy.error && (
        <div
          className="mk-alert mk-alert--danger"
          role="alert"
        >
          {strategy.error.message}
        </div>
      )}

      {strategy.active && (
        <div
          className="mk-progress"
          aria-live="polite"
        >
          <div>
            <span>전략 생성 중</span>
            <strong>
              분석 결과를 연결해 타깃, 메시지,
              채널 전략을 작성하고 있습니다.
            </strong>
          </div>
        </div>
      )}

      {!result && !strategy.active && (
        <section className="mk-strategy__empty">
          <h3>마케팅 전략을 생성하세요</h3>

          <p>
            시장·BM·기술운영·재무·패널 조사
            결과를 기반으로 실행 전략을 만듭니다.
          </p>

          <button
            className="mk-primary"
            type="button"
            disabled={!view?.ready}
            onClick={() => {
              void strategy.generate();
            }}
          >
            마케팅 전략 생성
          </button>
        </section>
      )}

      {result && (
        <section className="mk-strategy__result">
          {view.stale && (
            <div
              className="mk-alert mk-alert--danger"
              role="alert"
            >
              이전 분석 결과로 생성된 전략입니다.
              최신 데이터로 다시 생성해 주세요.
            </div>
          )}

          <div className="mk-strategy__summary">
            <p>전략 요약</p>
            <h3>{result.executiveSummary}</h3>
          </div>

          <StrategyList
            title="타깃 고객"
            values={result.targetCustomers}
          />

          <section className="mk-strategy__section">
            <h3>포지셔닝</h3>
            <p>{result.positioning}</p>
          </section>

          <StrategyList
            title="핵심 메시지"
            values={result.coreMessages}
          />

          <section className="mk-strategy__section">
            <h3>채널 전략</h3>

            <div className="mk-strategy__cards">
              {result.channelStrategies
                .map((channel) => (
                  <article key={channel.channel}>
                    <strong>
                      {channel.channel}
                    </strong>
                    <p>{channel.objective}</p>
                    <small>
                      {channel.rationale}
                    </small>
                  </article>
                ))}
            </div>
          </section>

          <StrategyList
            title="콘텐츠 축"
            values={result.contentPillars}
          />

          <StrategyList
            title="위험 및 주의사항"
            values={result.risks}
          />

          <footer className="mk-strategy__actions">
            <button
              type="button"
              disabled={strategy.downloading}
              onClick={() => {
                void strategy.download();
              }}
            >
              {strategy.downloading
                ? 'PDF 생성 중…'
                : '전략 PDF 다운로드'}
            </button>

            <button
              type="button"
              disabled={strategy.active}
              onClick={() => {
                void strategy.generate();
              }}
            >
              최신 데이터로 다시 생성
            </button>

            <button
              className="mk-primary"
              type="button"
              disabled={!strategy.current}
              onClick={onNext}
            >
              이 전략으로 콘텐츠 만들기
            </button>
          </footer>
        </section>
      )}
    </div>
  );
}