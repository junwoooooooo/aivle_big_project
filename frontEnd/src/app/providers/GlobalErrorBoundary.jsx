import { Component } from 'react';

import { ErrorState } from '../../shared/ui/index.js';

export default class GlobalErrorBoundary extends Component {
  constructor(props) {
    super(props);
    this.state = { hasError: false };
  }

  static getDerivedStateFromError() {
    return { hasError: true };
  }

  render() {
    if (this.state.hasError) {
      return (
        <main className="app-main__inner">
          <ErrorState
            title="화면을 표시하지 못했습니다"
            description="입력한 내용은 가능한 한 유지됩니다. 화면을 새로고침해 다시 시도해 주세요."
            onRetry={() => window.location.reload()}
          />
        </main>
      );
    }
    return this.props.children;
  }
}

