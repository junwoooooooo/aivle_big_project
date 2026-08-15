import { useCallback, useEffect, useState } from 'react';

import { useApiClient } from '../../../shared/api/ApiClientProvider.jsx';
import { createProjectApi } from '../api/projectApi.js';
import { toProjectViewModel } from '../model/projectViewModel.js';

function mapProjects(projects) {
  return projects
    .map(toProjectViewModel)
    .sort((a, b) => new Date(b.updatedAt) - new Date(a.updatedAt));
}

export function useProjects() {
  const client = useApiClient();
  const [state, setState] = useState({
    status: 'loading',
    projects: [],
    error: null,
  });

  const load = useCallback(async () => {
    setState((current) => ({ ...current, status: 'loading', error: null }));
    try {
      const projects = await createProjectApi(client).list();
      setState({ status: 'success', projects: mapProjects(projects), error: null });
    } catch (error) {
      setState({ status: 'error', projects: [], error });
    }
  }, [client]);

  useEffect(() => {
    let active = true;
    createProjectApi(client).list()
      .then((projects) => {
        if (active) {
          setState({ status: 'success', projects: mapProjects(projects), error: null });
        }
      })
      .catch((error) => {
        if (active) setState({ status: 'error', projects: [], error });
      });
    return () => { active = false; };
  }, [client]);

  useEffect(() => {
    const refreshOnFocus = () => {
      createProjectApi(client).list().then((projects) => {
        setState({ status: 'success', projects: mapProjects(projects), error: null });
      }).catch(() => { /* 화면 복귀 갱신 실패는 기존 목록을 유지한다. */ });
    };
    window.addEventListener('focus', refreshOnFocus);
    return () => window.removeEventListener('focus', refreshOnFocus);
  }, [client]);

  return { ...state, retry: load };
}

export function useProject(projectId) {
  const client = useApiClient();
  const [state, setState] = useState({
    status: 'loading',
    project: null,
    error: null,
  });

  const load = useCallback(async () => {
    setState({ status: 'loading', project: null, error: null });
    try {
      const project = await createProjectApi(client).get(projectId);
      setState({
        status: 'success',
        project: toProjectViewModel(project),
        error: null,
      });
    } catch (error) {
      setState({ status: 'error', project: null, error });
    }
  }, [client, projectId]);

  useEffect(() => {
    let active = true;
    createProjectApi(client).get(projectId)
      .then((project) => {
        if (active) {
          setState({
            status: 'success',
            project: toProjectViewModel(project),
            error: null,
          });
        }
      })
      .catch((error) => {
        if (active) setState({ status: 'error', project: null, error });
      });
    return () => { active = false; };
  }, [client, projectId]);

  return { ...state, retry: load };
}
