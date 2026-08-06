import { useContext } from 'react';

import ServicePolicyContext from './servicePolicyContext.js';

export function useServicePolicy() {
  const value = useContext(ServicePolicyContext);
  if (!value) {
    throw new Error('useServicePolicy는 ServicePolicyProvider 안에서 사용해야 합니다.');
  }
  return value;
}
