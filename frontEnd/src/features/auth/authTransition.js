export function buildAuthSwitchState(target) {
  return { authTransition: true, source: 'auth-switch', direction: target === 'signup' ? 'to-signup' : 'to-login', intent: target };
}
