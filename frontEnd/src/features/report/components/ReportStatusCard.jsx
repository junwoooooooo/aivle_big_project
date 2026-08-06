import { Link } from 'react-router-dom';

import { Badge, Card } from '../../../shared/ui/index.js';

export default function ReportStatusCard({ section, compact = false }) {
  return (
    <Card className={`report-status-card ${compact ? 'report-status-card--compact' : ''}`}>
      <div className="report-status-card__heading">
        <h3>{section.title}</h3>
        <Badge tone={section.statusView.tone}>{section.statusView.label}</Badge>
      </div>
      <p>{section.summary}</p>
      {section.error && (
        <p className="report-status-card__error" role="alert">
          이 영역을 불러오지 못했습니다. 상세 화면에서 다시 시도해 주세요.
        </p>
      )}
      <Link to={section.route}>
        {section.data ? '상세 결과 보기' : '이 단계로 이동'}
      </Link>
    </Card>
  );
}
