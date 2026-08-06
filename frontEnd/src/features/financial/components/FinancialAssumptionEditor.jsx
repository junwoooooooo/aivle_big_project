const COMMON_FIELDS = [
  ['monthlyGrowthRate', '월 성장률', '%', '월별 판매량 증감 가정입니다. -100%보다 커야 합니다.'],
  ['unitVariableCost', '단위 변동비', '원', '판매 또는 활성 구독자 한 명당 발생하는 비용입니다.'],
  ['paymentFeeRate', '결제 수수료율', '%', '매출에 비례하는 결제 수수료입니다.'],
  ['otherVariableCostPerUnit', '기타 단위 변동비', '원', '포장·배송 등 기타 단위 비용입니다.'],
  ['monthlyLaborCost', '월 인건비', '원', '매월 반복되는 인건비입니다.'],
  ['monthlyMarketingCost', '월 마케팅비', '원', '매월 반복되는 마케팅 비용입니다.'],
  ['monthlyInfrastructureCost', '월 인프라비', '원', '서버·도구 등 월 운영 비용입니다.'],
  ['monthlyRentCost', '월 임차료', '원', '사무실·장비 임차 비용입니다.'],
  ['monthlyOtherFixedCost', '기타 월 고정비', '원', '기타 반복 고정비입니다.'],
  ['initialDevelopmentCost', '초기 개발비', '원', '분석 시작 전 필요한 개발 투자입니다.'],
  ['initialEquipmentCost', '초기 장비비', '원', '초기 장비 취득 비용입니다.'],
  ['initialMarketingCost', '초기 마케팅비', '원', '출시 전 집행할 마케팅 비용입니다.'],
  ['initialOtherCost', '기타 초기 비용', '원', '기타 초기 투자 비용입니다.'],
];

function AmountField({ field, label, unit, help, value, onChange, disabled, error }) {
  const helpId = `financial-${field}-help`;
  const errorId = `financial-${field}-error`;
  return (
    <label className="financial-field">
      <span>{label} <small>{unit}</small></span>
      <input
        inputMode="decimal"
        value={value ?? ''}
        disabled={disabled}
        aria-invalid={Boolean(error)}
        aria-describedby={`${helpId}${error ? ` ${errorId}` : ''}`}
        onChange={(event) => onChange(field, event.target.value)}
      />
      <small id={helpId}>{help}</small>
      {error && <small id={errorId} className="financial-field__error">{error}</small>}
    </label>
  );
}

export default function FinancialAssumptionEditor({
  assumptions,
  analysisPeriodMonths,
  onChange,
  onPeriodChange,
  disabled = false,
  errors = {},
  sourceLabel = '사용자 가정',
}) {
  const oneTime = assumptions.revenueModel !== 'SUBSCRIPTION';
  const subscription = assumptions.revenueModel !== 'ONE_TIME';
  return (
    <section className="financial-form" aria-labelledby="financial-assumptions-title">
      <div className="financial-form__heading">
        <h2 id="financial-assumptions-title">수익 모델과 비용 구조</h2>
        <span className="financial-source-badge">출처: {sourceLabel}</span>
      </div>
      <label className="financial-field">
        <span>수익 모델</span>
        <select value={assumptions.revenueModel} disabled={disabled} onChange={(event) => onChange('revenueModel', event.target.value)}>
          <option value="ONE_TIME">일회성 판매</option><option value="SUBSCRIPTION">구독</option><option value="MIXED">혼합</option>
        </select>
        <small>실제 사업 구조와 가까운 방식을 선택하세요.</small>
      </label>
      <label className="financial-field">
        <span>분석 기간</span>
        <select value={analysisPeriodMonths} disabled={disabled} onChange={(event) => onPeriodChange(Number(event.target.value))}>
          {[12, 24, 36].map((months) => <option key={months} value={months}>{months}개월</option>)}
        </select>
        <small>12·24·36개월 중 하나를 선택합니다.</small>
      </label>
      {oneTime && <>
        <AmountField field="unitPrice" label="판매 단가" unit="원" help="한 단위의 판매 가격입니다." value={assumptions.unitPrice} onChange={onChange} disabled={disabled} error={errors.unitPrice} />
        <AmountField field="monthlySalesVolume" label="월 판매량" unit="건" help="첫 달의 예상 판매 수량입니다." value={assumptions.monthlySalesVolume} onChange={onChange} disabled={disabled} error={errors.monthlySalesVolume} />
      </>}
      {subscription && <>
        <AmountField field="monthlySubscriptionPrice" label="월 구독료" unit="원" help="구독자 한 명의 월 구독료입니다." value={assumptions.monthlySubscriptionPrice} onChange={onChange} disabled={disabled} error={errors.monthlySubscriptionPrice} />
        <AmountField field="initialSubscribers" label="초기 구독자" unit="명" help="첫 달 시작 시점의 구독자 수입니다." value={assumptions.initialSubscribers} onChange={onChange} disabled={disabled} error={errors.initialSubscribers} />
        <AmountField field="monthlyNewSubscribers" label="월 신규 구독자" unit="명" help="매월 새로 유입될 구독자 수입니다." value={assumptions.monthlyNewSubscribers} onChange={onChange} disabled={disabled} error={errors.monthlyNewSubscribers} />
        <AmountField field="monthlyChurnRate" label="월 이탈률" unit="%" help="월초 구독자 중 이탈할 비율입니다." value={assumptions.monthlyChurnRate} onChange={onChange} disabled={disabled} error={errors.monthlyChurnRate} />
      </>}
      {COMMON_FIELDS.map(([field, label, unit, help]) => <AmountField key={field} field={field} label={label} unit={unit} help={help} value={assumptions[field]} onChange={onChange} disabled={disabled} error={errors[field]} />)}
    </section>
  );
}
