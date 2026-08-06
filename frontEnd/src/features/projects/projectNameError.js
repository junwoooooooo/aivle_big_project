export const PROJECT_NAME_ALREADY_EXISTS_MESSAGE = '같은 이름의 프로젝트가 이미 있습니다.';

export function getProjectNameError(error) {
  if (error?.status === 409 && error?.code === 'PROJECT_NAME_ALREADY_EXISTS') return PROJECT_NAME_ALREADY_EXISTS_MESSAGE;
  return null;
}
