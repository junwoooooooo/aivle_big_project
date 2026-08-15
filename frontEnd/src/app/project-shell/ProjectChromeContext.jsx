/* eslint-disable react-refresh/only-export-components */
import { createContext, useCallback, useContext, useMemo, useState } from 'react';

const ProjectChromeContext = createContext(null);

export function ProjectChromeProvider({ children }) {
  const [model, setModel] = useState(null);
  const register = useCallback((nextModel) => {
    setModel(nextModel);
    return () => setModel((current) => current === nextModel ? null : current);
  }, []);
  const value = useMemo(() => ({ model, register }), [model, register]);
  return <ProjectChromeContext.Provider value={value}>{children}</ProjectChromeContext.Provider>;
}

export function useProjectChrome() {
  const value = useContext(ProjectChromeContext);
  if (!value) throw new Error('useProjectChrome must be used inside ProjectChromeProvider');
  return value;
}
