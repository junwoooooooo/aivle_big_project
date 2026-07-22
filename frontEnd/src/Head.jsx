import React from 'react';
import { Link } from 'react-router-dom';

function Head() {
    return (
        <header className="header">
            {/* 로고 (클릭 시 홈으로 이동) */}
            <div className="logo-group">
                <Link to="/" className="logo-text">
                    페르소나 플랫폼
                </Link>
            </div>

            {/* 내비게이션 메뉴 */}
            <nav className="nav">
                <Link to="/service" className="nav-link">서비스 이용</Link>
                <Link to="/contact" className="nav-link">고객 문의</Link>
            </nav>

            {/* 로그인 아이콘 버튼 (클릭 시 /login 페이지로 이동) */}
            <div className="user-section">
                <Link to="/login" className="login-button" aria-label="로그인">
                    <svg
                        width="24"
                        height="24"
                        viewBox="0 0 24 24"
                        fill="none"
                        stroke="currentColor"
                        strokeWidth="2"
                        strokeLinecap="round"
                        strokeLinejoin="round"
                    >
                        <path d="M20 21v-2a4 4 0 0 0-4-4H8a4 4 0 0 0-4 4v2" />
                        <circle cx="12" cy="7" r="4" />
                    </svg>
                </Link>
            </div>
        </header>
    );
}

export default Head;