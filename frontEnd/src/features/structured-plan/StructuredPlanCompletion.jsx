import { useEffect, useMemo, useRef, useState } from 'react';
import { Link } from 'react-router-dom';

import { useApiClient } from '../../shared/api/ApiClientProvider.jsx';
import { getUserErrorMessage } from '../../shared/api/apiError.js';
import {
  Alert,
  Badge,
  Button,
  Card,
  Dialog,
  Progress,
  StatusBadge,
  Textarea,
} from '../../shared/ui/index.js';
import { useProjectContext } from '../projects/ProjectContext.jsx';
import { createStructuredPlanApi } from './api/structuredPlanApi.js';
import { toStructuredPlanViewModel } from './model/structuredPlanViewModel.js';
import { useServicePolicy } from '../service-policy/useServicePolicy.js';
import { getWriteRestriction, isServicePolicyError } from '../service-policy/servicePolicyRestrictions.js';

const FILTERS = [
  ['all', '전체'],
  ['OPEN', '보완 필요'],
  ['FILLED', '입력 완료'],
  ['WAIVED', '제외'],
];

function hasControlCharacter(value) {
  return [...value].some((character) => {
    const code = character.codePointAt(0);
    return (code >= 0 && code <= 8)
      || code === 11
      || code === 12
      || (code >= 14 && code <= 31)
      || code === 127;
  });
}

function validateDraft(mode, value) {
  const trimmed = value.trim();
  if (!trimmed) {
    return mode === 'FILLED'
      ? '보완할 내용을 입력해 주세요.'
      : '이번 단계에서 제외하는 이유를 입력해 주세요.';
  }
  const maximum = mode === 'FILLED' ? 4000 : 500;
  if (value.length > maximum) return `최대 ${maximum.toLocaleString('ko-KR')}자까지 입력할 수 있습니다.`;
  if (hasControlCharacter(value)) return '제어 문자는 입력할 수 없습니다.';
  return '';
}

function formatConfirmedAt(value) {
  if (!value) return '확정 시각 정보 없음';
  const date = new Date(value);
  if (Number.isNaN(date.getTime())) return '확정 시각 확인 필요';
  return new Intl.DateTimeFormat('ko-KR', {
    dateStyle: 'medium',
    timeStyle: 'short',
  }).format(date);
}

function draftKey(fieldId, mode) {
  return `${fieldId}:${mode}`;
}

export function StructuredPlanCompletion({
  projectId,
  plan,
  onPlanChange,
  sourceIsLatest = true,
}) {
  const client = useApiClient();
  const servicePolicy = useServicePolicy();
  const restriction = getWriteRestriction(servicePolicy);
  const { retry: refreshProject } = useProjectContext();
  const api = useMemo(() => createStructuredPlanApi(client), [client]);
  const errorRef = useRef(null);
  const summaryRef = useRef(null);
  const [filter, setFilter] = useState('all');
  const [editor, setEditor] = useState(null);
  const [drafts, setDrafts] = useState({});
  const [fieldErrors, setFieldErrors] = useState({});
  const [busyFieldId, setBusyFieldId] = useState(null);
  const [waiveFieldId, setWaiveFieldId] = useState(null);
  const [conflict, setConflict] = useState(null);
  const [actionError, setActionError] = useState('');
  const [successMessage, setSuccessMessage] = useState('');
  const [confirmOpen, setConfirmOpen] = useState(false);
  const [confirming, setConfirming] = useState(false);
  const [confirmConflict, setConfirmConflict] = useState('');

  const activeField = plan.missingFields.find((field) => field.fieldId === editor?.fieldId);
  const waiverField = plan.missingFields.find((field) => field.fieldId === waiveFieldId);
  const activeDraftKey = activeField ? draftKey(activeField.fieldId, 'FILLED') : null;
  const activeDraft = activeDraftKey ? drafts[activeDraftKey] ?? '' : '';
  const waiverDraftKey = waiverField ? draftKey(waiverField.fieldId, 'WAIVED') : null;
  const waiverDraft = waiverDraftKey ? drafts[waiverDraftKey] ?? '' : '';
  const dirty = Boolean(
    (activeField && activeDraft !== (activeField.userValue ?? ''))
    || (waiverField && waiverDraft !== (waiverField.status === 'WAIVED' ? waiverField.reason ?? '' : '')),
  );

  useEffect(() => {
    if (!dirty) return undefined;
    const warnBeforeUnload = (event) => {
      event.preventDefault();
      event.returnValue = '';
    };
    window.addEventListener('beforeunload', warnBeforeUnload);
    return () => window.removeEventListener('beforeunload', warnBeforeUnload);
  }, [dirty]);

  const filteredFields = plan.missingFields.filter(
    (field) => filter === 'all'
      || field.status === filter
      || String(field.fieldId) === String(conflict?.fieldId),
  );
  const counts = {
    OPEN: plan.missingFields.filter((field) => field.status === 'OPEN').length,
    FILLED: plan.missingFields.filter((field) => field.status === 'FILLED').length,
    WAIVED: plan.missingFields.filter((field) => field.status === 'WAIVED').length,
  };
  const mutationInProgress = busyFieldId != null;
  const confirmReady = sourceIsLatest
    && plan.status === 'DRAFT'
    && Number(plan.completionRate) === 100
    && plan.openRequiredCount === 0
    && !mutationInProgress
    && !restriction.blocked
    && plan.lockVersion != null;
  const confirmBlockers = [
    !sourceIsLatest && '최신 문서 버전의 구조화 결과가 아직 준비되지 않았습니다.',
    plan.status === 'NEEDS_INPUT' && '필수 보완 항목을 모두 해결해 주세요.',
    plan.status !== 'DRAFT' && plan.status !== 'NEEDS_INPUT'
      && plan.status !== 'CONFIRMED' && '서버의 계획 상태를 다시 확인해 주세요.',
    Number(plan.completionRate) !== 100 && '서버 완성도가 100%가 되어야 합니다.',
    plan.openRequiredCount > 0 && `필수 보완 항목 ${plan.openRequiredCount}개가 남아 있습니다.`,
    mutationInProgress && '보완 항목 저장이 끝날 때까지 기다려 주세요.',
    restriction.blocked && restriction.message,
    plan.lockVersion == null && '최신 잠금 버전을 확인할 수 없습니다.',
  ].filter(Boolean);

  async function refreshPlan() {
    const latest = toStructuredPlanViewModel(await api.getLatest(projectId));
    onPlanChange(latest);
    return latest;
  }

  function beginFill(field) {
    const key = draftKey(field.fieldId, 'FILLED');
    setDrafts((current) => ({
      ...current,
      [key]: current[key] ?? field.userValue ?? '',
    }));
    setEditor({ fieldId: field.fieldId });
    setFieldErrors((current) => ({ ...current, [key]: '' }));
    setConflict(null);
    setActionError('');
    setSuccessMessage('');
  }

  function beginWaive(field) {
    const key = draftKey(field.fieldId, 'WAIVED');
    setDrafts((current) => ({
      ...current,
      [key]: current[key] ?? (field.status === 'WAIVED' ? field.reason ?? '' : ''),
    }));
    setWaiveFieldId(field.fieldId);
    setFieldErrors((current) => ({ ...current, [key]: '' }));
    setConflict(null);
    setActionError('');
    setSuccessMessage('');
  }

  async function submitField(field, mode, value, lockVersion = field.lockVersion) {
    const key = draftKey(field.fieldId, mode);
    const validationError = validateDraft(mode, value);
    if (validationError) {
      setFieldErrors((current) => ({ ...current, [key]: validationError }));
      return;
    }
    if (busyFieldId != null || plan.status === 'CONFIRMED' || !sourceIsLatest || restriction.blocked) return;

    setBusyFieldId(field.fieldId);
    setActionError('');
    setSuccessMessage('');
    setFieldErrors((current) => ({ ...current, [key]: '' }));
    try {
      await api.updateMissingField(projectId, plan.planId, field.fieldId, {
        status: mode,
        ...(mode === 'FILLED' ? { value } : { reason: value }),
        version: lockVersion,
      });
      await refreshPlan();
      setEditor(null);
      setWaiveFieldId(null);
      setConflict(null);
      setSuccessMessage(mode === 'FILLED'
        ? '보완 내용을 저장했습니다.'
        : '해당 항목을 이번 단계에서 제외했습니다.');
      requestAnimationFrame(() => summaryRef.current?.focus());
    } catch (error) {
      if (error.status === 409 || error.code === 'RESOURCE_VERSION_CONFLICT') {
        try {
          const latest = await refreshPlan();
          const latestField = latest.missingFields.find(
            (item) => String(item.fieldId) === String(field.fieldId),
          );
          if (!latestField || latest.status === 'CONFIRMED') {
            setEditor(null);
            setWaiveFieldId(null);
            setActionError(latest.status === 'CONFIRMED'
              ? '사업계획이 이미 확정되어 보완할 수 없습니다.'
              : '최신 결과에서 이 보완 항목을 찾을 수 없습니다.');
          } else {
            setConflict({
              fieldId: field.fieldId,
              mode,
              draft: value,
              latestField,
            });
            if (mode === 'WAIVED') setWaiveFieldId(null);
          }
        } catch (refreshError) {
          setActionError(getUserErrorMessage(refreshError));
        }
      } else {
        if (isServicePolicyError(error)) {
          void servicePolicy.refresh().catch(() => undefined);
        }
        setActionError(getUserErrorMessage(error));
      }
      requestAnimationFrame(() => errorRef.current?.focus());
    } finally {
      setBusyFieldId(null);
    }
  }

  function discardConflict() {
    if (conflict) {
      const key = draftKey(conflict.fieldId, conflict.mode);
      setDrafts((current) => {
        const next = { ...current };
        delete next[key];
        return next;
      });
    }
    setConflict(null);
    setEditor(null);
    setWaiveFieldId(null);
  }

  async function confirmPlan() {
    if (!confirmReady || confirming || restriction.blocked) return;
    setConfirming(true);
    setActionError('');
    setConfirmConflict('');
    try {
      const confirmed = toStructuredPlanViewModel(
        await api.confirm(projectId, plan.planId, { version: plan.lockVersion }),
      );
      onPlanChange(confirmed);
      setConfirmOpen(false);
      setSuccessMessage('사업계획을 확정했습니다. 법률·규제 검토 단계로 이동할 수 있습니다.');
      await refreshProject();
    } catch (error) {
      if (error.status === 409 || error.code === 'RESOURCE_VERSION_CONFLICT') {
        try {
          const latest = await refreshPlan();
          setConfirmOpen(false);
          if (latest.status === 'CONFIRMED') {
            setSuccessMessage('다른 요청에서 이미 확정된 최신 상태를 불러왔습니다.');
            await refreshProject();
          } else {
            setConfirmConflict(
              '다른 변경사항이 먼저 저장되었습니다. 최신 계획을 검토한 뒤 다시 확정해 주세요.',
            );
          }
        } catch (refreshError) {
          setActionError(getUserErrorMessage(refreshError));
        }
      } else {
        if (isServicePolicyError(error)) {
          void servicePolicy.refresh().catch(() => undefined);
        }
        setActionError(getUserErrorMessage(error));
      }
      requestAnimationFrame(() => errorRef.current?.focus());
    } finally {
      setConfirming(false);
    }
  }

  return (
    <section className="plan-completion" aria-labelledby="plan-completion-title">
      <Card className="completion-summary">
        <div className="completion-summary__heading">
          <div>
            <span>계획 상태</span>
            <h2 id="plan-completion-title">보완 및 확정</h2>
          </div>
          <StatusBadge status={plan.status} />
        </div>
        <Progress value={plan.completionRate} label="서버가 계산한 사업계획 완성도" />
        <div className="completion-counts" aria-label="보완 항목 상태">
          <span>보완 필요 <strong>{counts.OPEN}</strong></span>
          <span>입력 완료 <strong>{counts.FILLED}</strong></span>
          <span>제외 <strong>{counts.WAIVED}</strong></span>
        </div>
        <p>완성도와 계획 상태는 저장 후 서버가 재계산한 최신 결과입니다.</p>
      </Card>

      <div ref={summaryRef} tabIndex="-1" aria-live="polite">
        {successMessage && <Alert title="변경사항 반영 완료" tone="success">{successMessage}</Alert>}
        {confirmConflict && <Alert title="최신 계획을 다시 확인해 주세요" tone="warning">{confirmConflict}</Alert>}
        {actionError && (
          <div ref={errorRef} tabIndex="-1">
            <Alert title="요청을 완료하지 못했습니다" tone="danger">{actionError}</Alert>
          </div>
        )}
      </div>

      {!sourceIsLatest && (
        <Alert title="새 문서 버전의 결과를 확인하고 있습니다" tone="warning">
          이전 계획은 편집하지 않습니다. 최신 분석 작업이 끝난 뒤 새 계획을 다시 불러오세요.
        </Alert>
      )}
      {restriction.blocked && (
        <Alert tone={restriction.code === 'POLICY_UNAVAILABLE' ? 'danger' : 'warning'} title="계획을 변경할 수 없습니다">
          <p>{restriction.message}</p>
          {restriction.code === 'POLICY_UNAVAILABLE' && <Button type="button" variant="outline" size="small" onClick={() => void servicePolicy.refresh().catch(() => undefined)}>다시 시도</Button>}
        </Alert>
      )}

      {plan.status === 'CONFIRMED' ? (
        <Card className="confirmed-plan">
          <div>
            <Badge tone="success">확정 완료</Badge>
            <h2>구조화된 사업계획이 확정되었습니다</h2>
            <p>확정된 보완 내용은 읽기 전용이며 취소하거나 다시 열 수 없습니다.</p>
          </div>
          <dl>
            <div><dt>확정 시각</dt><dd>{formatConfirmedAt(plan.confirmedAt)}</dd></div>
            <div><dt>확정 사용자</dt><dd>{plan.confirmedBy != null ? `사용자 #${plan.confirmedBy}` : '정보 없음'}</dd></div>
            <div><dt>출처 문서 버전</dt><dd>{plan.versionNumber}</dd></div>
          </dl>
          <Link className="primary-link" to={`/app/projects/${projectId}/legal`}>
            법률·규제 검토 단계로 이동
          </Link>
        </Card>
      ) : (
        <>
          <div className="missing-field-toolbar">
            <div>
              <h2>보완 항목 {plan.missingFields.length}개</h2>
              <p>보완 필요 항목과 중요도가 높은 항목부터 표시합니다.</p>
            </div>
            <div className="missing-field-filter" role="group" aria-label="보완 항목 필터">
              {FILTERS.map(([value, label]) => (
                <Button
                  key={value}
                  type="button"
                  variant={filter === value ? 'primary' : 'outline'}
                  aria-pressed={filter === value}
                  onClick={() => setFilter(value)}
                >
                  {label}
                </Button>
              ))}
            </div>
          </div>

          {plan.missingFields.length === 0 ? (
            <Alert title="보완할 필수 항목이 없습니다" tone="success">
              서버 완성도와 계획 상태를 확인한 뒤 확정할 수 있습니다.
            </Alert>
          ) : (
            <div className="missing-field-list">
              {filteredFields.map((field) => {
                const key = draftKey(field.fieldId, 'FILLED');
                const value = drafts[key] ?? field.userValue ?? '';
                const fieldConflict = conflict?.fieldId === field.fieldId ? conflict : null;
                return (
                  <Card as="article" className="missing-field-card" key={field.fieldId}>
                    <header>
                      <div>
                        <span className="missing-field-card__section">{field.sectionDisplayName}</span>
                        <h3>{field.label}</h3>
                      </div>
                      <StatusBadge status={field.status} />
                    </header>
                    <div className="missing-field-meta">
                      <Badge tone={field.required ? 'warning' : 'neutral'}>
                        {field.required ? '필수' : '선택'}
                      </Badge>
                      <span>{field.priorityView.label}</span>
                    </div>
                    <p className="missing-field-reason">
                      <strong>분석에서 확인되지 않은 이유</strong>
                      {field.reason || '서버가 별도 이유를 제공하지 않았습니다.'}
                    </p>
                    {field.status === 'FILLED' && (
                      <div className="missing-field-saved">
                        <strong>저장된 보완 내용</strong>
                        <p>{field.userValue || '저장된 내용이 없습니다.'}</p>
                      </div>
                    )}
                    {field.status === 'WAIVED' && (
                      <div className="missing-field-saved">
                        <strong>제외 사유</strong>
                        <p>{field.reason || '저장된 제외 사유가 없습니다.'}</p>
                      </div>
                    )}

                    {editor?.fieldId === field.fieldId && (
                      <form onSubmit={(event) => {
                        event.preventDefault();
                        submitField(field, 'FILLED', value);
                      }}>
                        <Textarea
                          label="보완 내용"
                          description={`분석 원문을 바꾸지 않고 별도 사용자 입력으로 저장합니다. ${value.length.toLocaleString('ko-KR')} / 4,000자`}
                          value={value}
                          maxLength="4000"
                          error={fieldErrors[key]}
                          onChange={(event) => setDrafts((current) => ({
                            ...current,
                            [key]: event.target.value,
                          }))}
                          onKeyDown={(event) => {
                            if ((event.ctrlKey || event.metaKey) && event.key === 'Enter') {
                              event.preventDefault();
                              submitField(field, 'FILLED', value);
                            }
                          }}
                          disabled={busyFieldId === field.fieldId || restriction.blocked}
                          required
                        />
                        <div className="missing-field-actions">
                          <Button type="submit" loading={busyFieldId === field.fieldId} disabled={restriction.blocked}>
                            보완 내용 저장
                          </Button>
                          <Button
                            type="button"
                            variant="outline"
                            disabled={busyFieldId === field.fieldId}
                            onClick={() => setEditor(null)}
                          >
                            취소
                          </Button>
                        </div>
                      </form>
                    )}

                    {fieldConflict && (
                      <Alert title="다른 변경사항이 먼저 저장되었습니다" tone="warning">
                        <p>최신 서버 내용을 불러왔습니다. 내 입력은 아직 전송하지 않고 보존했습니다.</p>
                        <dl className="conflict-comparison">
                          <div>
                            <dt>서버 최신 상태</dt>
                            <dd>{fieldConflict.latestField.status}</dd>
                          </div>
                          <div>
                            <dt>서버 최신 내용</dt>
                            <dd>{fieldConflict.mode === 'FILLED'
                              ? fieldConflict.latestField.userValue || '입력 없음'
                              : fieldConflict.latestField.reason || '사유 없음'}</dd>
                          </div>
                          <div>
                            <dt>내 입력</dt>
                            <dd>{fieldConflict.draft}</dd>
                          </div>
                        </dl>
                        <div className="missing-field-actions">
                          <Button
                            type="button"
                            disabled={restriction.blocked}
                            onClick={() => submitField(
                              fieldConflict.latestField,
                              fieldConflict.mode,
                              fieldConflict.draft,
                              fieldConflict.latestField.lockVersion,
                            )}
                          >
                            최신 버전에 다시 저장
                          </Button>
                          <Button type="button" variant="outline" onClick={discardConflict}>
                            취소
                          </Button>
                        </div>
                      </Alert>
                    )}

                    {field.isEditable && sourceIsLatest && editor?.fieldId !== field.fieldId && (
                      <div className="missing-field-actions">
                        <Button type="button" disabled={restriction.blocked} onClick={() => beginFill(field)}>
                          {field.status === 'FILLED' ? '입력 수정' : '내용 입력'}
                        </Button>
                        <Button type="button" variant="outline" disabled={restriction.blocked} onClick={() => beginWaive(field)}>
                          {field.status === 'WAIVED' ? '제외 사유 수정' : '이번 단계에서 제외'}
                        </Button>
                      </div>
                    )}
                  </Card>
                );
              })}
            </div>
          )}

          <Card className="plan-confirmation">
            <div>
              <StatusBadge status={plan.status} />
              <h2>사업계획 확정</h2>
              <p>확정은 자동으로 실행되지 않으며 서버가 최종 조건을 다시 검증합니다.</p>
            </div>
            {!confirmReady && confirmBlockers.length > 0 && (
              <ul>{confirmBlockers.map((reason) => <li key={reason}>{reason}</li>)}</ul>
            )}
            <Button type="button" disabled={!confirmReady} onClick={() => setConfirmOpen(true)}>
              사업계획 확정
            </Button>
          </Card>
        </>
      )}

      <Dialog
        open={Boolean(waiverField)}
        onClose={() => {
          if (busyFieldId == null) setWaiveFieldId(null);
        }}
        title="보완 항목 제외"
      >
        {waiverField && (
          <form onSubmit={(event) => {
            event.preventDefault();
            submitField(waiverField, 'WAIVED', waiverDraft);
          }}>
            <Alert title={waiverField.required ? '필수 항목을 제외합니다' : '항목을 제외합니다'} tone="warning">
              이 항목은 해결된 것으로 판정하는 대신 이번 검토 단계에서 제공하지 않는 것으로 기록됩니다.
            </Alert>
            <Textarea
              label="제외 사유"
              description={`${waiverDraft.length.toLocaleString('ko-KR')} / 500자`}
              value={waiverDraft}
              maxLength="500"
              error={fieldErrors[waiverDraftKey]}
              onChange={(event) => setDrafts((current) => ({
                ...current,
                [waiverDraftKey]: event.target.value,
              }))}
              disabled={busyFieldId === waiverField.fieldId || restriction.blocked}
              required
            />
            <div className="missing-field-actions">
              <Button type="submit" loading={busyFieldId === waiverField.fieldId} disabled={restriction.blocked}>
                제외 사유 저장
              </Button>
              <Button
                type="button"
                variant="outline"
                disabled={busyFieldId === waiverField.fieldId}
                onClick={() => setWaiveFieldId(null)}
              >
                취소
              </Button>
            </div>
          </form>
        )}
      </Dialog>

      <Dialog
        open={confirmOpen}
        onClose={() => {
          if (!confirming) setConfirmOpen(false);
        }}
        title="사업계획을 확정하시겠습니까?"
      >
        <Alert title="확정 후에는 수정할 수 없습니다" tone="warning">
          입력·제외 내용을 마지막으로 확인해 주세요. 확정하면 프로젝트가 법률·규제 검토 단계로 이동합니다.
        </Alert>
        <div className="confirm-review">
          <p>서버 완성도 <strong>{plan.completionRate}%</strong></p>
          <p>보완 필요 필수 항목 <strong>{plan.openRequiredCount}개</strong></p>
          <p>출처 문서 버전 <strong>{plan.versionNumber}</strong></p>
        </div>
        <div className="missing-field-actions">
          <Button type="button" loading={confirming} disabled={restriction.blocked} onClick={confirmPlan}>
            확인하고 확정
          </Button>
          <Button
            type="button"
            variant="outline"
            disabled={confirming}
            onClick={() => setConfirmOpen(false)}
          >
            취소
          </Button>
        </div>
      </Dialog>
    </section>
  );
}
