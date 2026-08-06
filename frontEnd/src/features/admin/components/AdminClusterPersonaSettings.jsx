import { useMemo, useState } from 'react';

import { Button } from '../../../shared/ui/index.js';
import { getAdminErrorMessage } from '../api/adminErrorResolver.js';
import useAdminPersonas from '../hooks/useAdminPersonas.js';
import AdminActionConfirmDialog from './AdminActionConfirmDialog.jsx';

function formatDate(value) {
  if (!value) return '변경 기록 없음';
  return new Intl.DateTimeFormat('ko-KR', {
    dateStyle: 'medium',
    timeStyle: 'short',
  }).format(new Date(value));
}

export default function AdminClusterPersonaSettings({ onChanged }) {
  const { data, loading, refreshing, error, refresh, api } = useAdminPersonas();
  const [pending, setPending] = useState(null);
  const enabled = useMemo(
    () => (data || []).filter((persona) => persona.enabled)
      .sort((left, right) => left.displayOrder - right.displayOrder),
    [data],
  );

  function beginVisibility(persona) {
    setPending({
      type: 'visibility',
      persona,
      nextEnabled: !persona.enabled,
    });
  }

  function beginMove(persona, offset) {
    const index = enabled.findIndex((item) => item.id === persona.id);
    const nextIndex = index + offset;
    if (index < 0 || nextIndex < 0 || nextIndex >= enabled.length) return;
    const ids = enabled.map((item) => item.id);
    [ids[index], ids[nextIndex]] = [ids[nextIndex], ids[index]];
    setPending({ type: 'order', persona, personaIds: ids, offset });
  }

  async function confirm({ reason }) {
    if (pending.type === 'visibility') {
      await api.updatePersonaVisibility(pending.persona.id, {
        enabled: pending.nextEnabled,
        reason,
      });
      onChanged?.(`${pending.persona.name} 노출 상태가 변경되었습니다.`);
    } else {
      await api.updatePersonaOrder({ personaIds: pending.personaIds, reason });
      onChanged?.('군집 페르소나 표시 순서가 변경되었습니다.');
    }
    setPending(null);
    await refresh();
  }

  return (
    <section className="admin-setting-section admin-persona-settings">
      <header>
        <h2>사용자에게 제공할 Persona</h2>
        <p>현재 카탈로그에서 최대 6개를 선택하고 표시 순서를 관리합니다.</p>
      </header>
      {loading && <p className="admin-query-progress" role="status">페르소나 카탈로그를 불러오는 중입니다.</p>}
      {refreshing && <p className="admin-query-progress" role="status">최신 노출 정책을 확인하고 있습니다.</p>}
      {error && (
        <div className="admin-error-state" role="alert">
          <p>{getAdminErrorMessage(error)}</p>
          <Button size="small" variant="outline" onClick={() => void refresh().catch(() => undefined)}>다시 시도</Button>
        </div>
      )}
      {data && (
        <>
          <p className="admin-persona-settings__count" role="status">
            사용자에게 표시 중 {enabled.length}/6
          </p>
          <div className="admin-persona-policy-grid">
            {data.map((persona) => {
              const enabledIndex = enabled.findIndex((item) => item.id === persona.id);
              return (
                <article className="admin-persona-policy-card" key={persona.id}>
                  <div className="admin-persona-policy-card__heading">
                    <span aria-hidden="true">{persona.name.slice(0, 1)}</span>
                    <div>
                      <h3>{persona.name}</h3>
                      <strong>{persona.enabled ? '사용자에게 표시 중' : '숨김'}</strong>
                    </div>
                  </div>
                  <p>{persona.summary}</p>
                  <ul aria-label="대표 키워드">
                    {persona.keywords.slice(0, 3).map((keyword) => <li key={keyword}>{keyword}</li>)}
                  </ul>
                  <dl>
                    <dt>마지막 변경</dt>
                    <dd>{persona.updatedBy?.displayName || persona.updatedBy?.username || '—'} · {formatDate(persona.updatedAt)}</dd>
                  </dl>
                  <div className="admin-persona-policy-card__actions">
                    <Button
                      size="small"
                      variant={persona.enabled ? 'outline' : 'primary'}
                      onClick={() => beginVisibility(persona)}
                    >
                      {persona.enabled ? '숨기기' : '표시하기'}
                    </Button>
                    {persona.enabled && (
                      <>
                        <Button size="small" variant="ghost" disabled={enabledIndex <= 0} onClick={() => beginMove(persona, -1)}>위로</Button>
                        <Button size="small" variant="ghost" disabled={enabledIndex === enabled.length - 1} onClick={() => beginMove(persona, 1)}>아래로</Button>
                      </>
                    )}
                  </div>
                </article>
              );
            })}
          </div>
        </>
      )}
      {pending && (
        <AdminActionConfirmDialog
          open
          title={pending.type === 'visibility' ? 'Persona 노출 변경' : 'Persona 표시 순서 변경'}
          description={pending.type === 'visibility'
            ? `${pending.persona.name}을(를) 사용자 선택 후보에서 ${pending.nextEnabled ? '표시' : '숨김'} 처리합니다.`
            : `${pending.persona.name}의 사용자 표시 순서를 변경합니다.`}
          targetLabel={pending.persona.name}
          currentState={pending.type === 'visibility'
            ? (pending.persona.enabled ? '표시 중' : '숨김')
            : `순서 ${pending.persona.displayOrder}`}
          nextState={pending.type === 'visibility'
            ? (pending.nextEnabled ? '표시 중' : '숨김')
            : (pending.offset < 0 ? '한 단계 위' : '한 단계 아래')}
          requiresReauthentication={false}
          confirmLabel="변경"
          onCancel={() => setPending(null)}
          onConfirm={confirm}
        />
      )}
    </section>
  );
}
