import { useContext } from 'react';
import { AuthMotionContext } from '../components/AuthMotionContext.jsx';

export default function useAuthMotion() {
  return useContext(AuthMotionContext);
}
