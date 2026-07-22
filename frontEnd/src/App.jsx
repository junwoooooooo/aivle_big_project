import React from 'react';
import { Routes, Route, useLocation } from 'react-router-dom';
import Head from './Head';
import './App.css';
import Contact from './page/Contact.jsx';
import Service from './page/Service.jsx';
import Login from "./page/Login.jsx";
import Admin from './page/Admin.jsx';
import VirtualMarket from './page/VirtualMarket.jsx';

// 메인 홈 화면 컴포넌트
function Home() {
    return (
        <main className="main-content">
            <div className="hero-card">
                <h2 className="hero-title">
                    유저가 없어도, 시장에 물어볼 수 있습니다
                </h2>
                <p className="hero-description">
                    출시 전 아이디어를 가상 고객 수백 명에게 미리 테스트하세요.<br/>
                    수백만 원짜리 시장조사가 몇 분 만에 — 컨셉·가격·기능까지.
                </p>
            </div>
        </main>
    );
}






function App() {
    const location = useLocation();
    const isAdminPage = location.pathname.startsWith('/admin');

    return (
        <div className="container">
            {/* 상단 헤더 */}
            {!isAdminPage && <Head />}

            {/* 라우트 설정 */}
            <Routes>
                <Route path="/" element={<Home />} />
                <Route path="/login" element={<Login />} />
                <Route path="/service" element={<Service />} />
                <Route path="/contact" element={<Contact />} />
                <Route path="/admin" element={<Admin />} />
                <Route path="/virtual-market" element={<VirtualMarket />} />
            </Routes>
        </div>
    );
}

export default App;