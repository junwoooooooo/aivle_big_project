import { useEffect, useMemo, useState } from 'react';

import { useApiClient } from '../../../shared/api/ApiClientProvider.jsx';
import { createValidationApi } from '../../validation/api/validationApi.js';

export default function useMarketingValidationSources(
  projectId,
  panelInterviewId,
  marketResponseId,
) {
  const client = useApiClient();
  const api = useMemo(() => createValidationApi(client), [client]);
  const [state, setState] = useState({
    panel: null,
    market: null,
    loading: Boolean(panelInterviewId || marketResponseId),
    error: null,
  });

  useEffect(() => {
    if (!panelInterviewId && !marketResponseId) {
      return undefined;
    }
    const controller = new AbortController();
    Promise.all([
      panelInterviewId
        ? api.interview(projectId, panelInterviewId, { signal: controller.signal })
        : Promise.resolve(null),
      marketResponseId
        ? api.marketResponse(projectId, marketResponseId, { signal: controller.signal })
        : Promise.resolve(null),
    ]).then(([panel, market]) => {
      setState({ panel, market, loading: false, error: null });
    }).catch((error) => {
      if (error?.code !== 'REQUEST_ABORTED') {
        setState({ panel: null, market: null, loading: false, error });
      }
    });
    return () => controller.abort();
  }, [api, marketResponseId, panelInterviewId, projectId]);

  return state;
}
