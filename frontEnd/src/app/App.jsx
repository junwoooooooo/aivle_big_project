import AppRouter from './router/AppRouter.jsx';
import { AuthTransitionProvider } from './transitions/AuthTransitionProvider.jsx';

export default function App() {
  return <AuthTransitionProvider><AppRouter /></AuthTransitionProvider>;
}
