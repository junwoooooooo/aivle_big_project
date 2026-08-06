/* eslint-disable react-refresh/only-export-components */
import { createContext, useContext } from 'react';

import { useProject } from './hooks/useProjects.js';

const ProjectContext = createContext(null);

export function ProjectProvider({ projectId, children }) {
  const value = useProject(projectId);
  return (
    <ProjectContext.Provider value={value}>
      {children}
    </ProjectContext.Provider>
  );
}

export function useProjectContext() {
  const context = useContext(ProjectContext);
  if (!context) {
    throw new Error('useProjectContext는 ProjectProvider 안에서 사용해야 합니다.');
  }
  return context;
}
