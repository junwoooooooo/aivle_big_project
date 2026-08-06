/* eslint-disable react-refresh/only-export-components */
import { createContext, useContext } from 'react';

const ApiClientContext = createContext(null);

export function ApiClientProvider({ client, children }) {
  return (
    <ApiClientContext.Provider value={client}>
      {children}
    </ApiClientContext.Provider>
  );
}

export function useApiClient() {
  const client = useContext(ApiClientContext);
  if (!client) {
    throw new Error('useApiClient는 ApiClientProvider 안에서 사용해야 합니다.');
  }
  return client;
}
