export const MAINTENANCE_MESSAGE = '현재 서비스 점검 중입니다. 조회 기능은 이용할 수 있지만 변경 작업은 잠시 사용할 수 없습니다.';
export const DOCUMENT_PROCESSING_MESSAGE = '현재 문서 처리 기능이 일시 중지되었습니다. 기존 문서와 분석 결과는 계속 확인할 수 있습니다.';
export const POLICY_UNAVAILABLE_MESSAGE = '서비스 운영 상태를 확인하지 못했습니다. 새 작업을 시작하기 전에 다시 시도해 주세요.';

export function getWriteRestriction({
  loading,
  policy,
  error,
  documentProcessing = false,
}) {
  if (loading) {
    return {
      blocked: true,
      code: 'POLICY_LOADING',
      message: '서비스 운영 상태를 확인하고 있습니다.',
    };
  }
  if (error) {
    return {
      blocked: true,
      code: 'POLICY_UNAVAILABLE',
      message: POLICY_UNAVAILABLE_MESSAGE,
    };
  }
  if (policy.maintenanceMode) {
    return {
      blocked: true,
      code: 'MAINTENANCE_MODE_ENABLED',
      message: MAINTENANCE_MESSAGE,
    };
  }
  if (documentProcessing && !policy.documentProcessingEnabled) {
    return {
      blocked: true,
      code: 'DOCUMENT_PROCESSING_DISABLED',
      message: DOCUMENT_PROCESSING_MESSAGE,
    };
  }
  return { blocked: false, code: null, message: '' };
}

export function isServicePolicyError(error) {
  return error?.code === 'MAINTENANCE_MODE_ENABLED'
    || error?.code === 'DOCUMENT_PROCESSING_DISABLED';
}
