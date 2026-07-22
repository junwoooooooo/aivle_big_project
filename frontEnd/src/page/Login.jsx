import React, { useState } from 'react';
import { Link } from 'react-router-dom';

import SignupPage from "./SignupPage.jsx";

function Login() {
    const [email, setEmail] = useState('');
    const [password, setPassword] = useState('');
    const [rememberMe, setRememberMe] = useState(false);

    const handleSubmit = (e) => {
        e.preventDefault();
        console.log({ email, password, rememberMe });
        alert('로그인 시도!');
    };

    return (
        <div className="login-wrapper">
            <form className="form-login" onSubmit={handleSubmit}>
                {/* 대문자 P 브랜드 로고 */}
                <div className="brand-logo mb-4">P</div>

                <h1 className="h3 mb-3 font-weight-normal">Please Login</h1>

                <label htmlFor="inputEmail" className="sr-only">
                    Email address
                </label>
                <input
                    type="email"
                    id="inputEmail"
                    className="form-control"
                    placeholder="Email address"
                    required
                    autoFocus
                    value={email}
                    onChange={(e) => setEmail(e.target.value)}
                />

                <label htmlFor="inputPassword" className="sr-only">
                    Password
                </label>
                <input
                    type="password"
                    id="inputPassword"
                    className="form-control"
                    placeholder="Password"
                    required
                    value={password}
                    onChange={(e) => setPassword(e.target.value)}
                />

                {/* Remember me & 회원가입 버튼 배치 영역 */}
                <div className="checkbox-signup-group mb-3">
                    <label className="remember-label">
                        <input
                            type="checkbox"
                            value="remember-me"
                            checked={rememberMe}
                            onChange={(e) => setRememberMe(e.target.checked)}
                        />{' '}
                        Remember me
                    </label>

                    <Link to="/signup" className="signup-link">
                        회원가입
                    </Link>
                </div>

                <button className="btn btn-lg btn-primary btn-block" type="submit">
                    Login
                </button>

                <p className="mt-5 mb-3 text-muted">©ktaivle @bootstrap</p>
            </form>
        </div>
    );
}

export default Login;