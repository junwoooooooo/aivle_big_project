import React, { useState } from 'react';
import { Link, useNavigate } from 'react-router-dom';

function SignupPage() {
    const [agreed, setAgreed] = useState(false);
    const [email, setEmail] = useState('');
    const [password, setPassword] = useState('');
    const [confirmPassword, setConfirmPassword] = useState('');

    const navigate = useNavigate();

    const handleSubmit = (e) => {
        e.preventDefault();

        // 1. 개인정보 약관 동의 체크 확인
        if (!agreed) {
            alert('개인정보 수집 및 이용 약관에 동의해야 회원가입이 가능합니다.');
            return;
        }

        // 2. 비밀번호 일치 확인
        if (password !== confirmPassword) {
            alert('비밀번호가 일치하지 않습니다. 다시 확인해 주세요.');
            return;
        }

        // 가입 처리 하드코딩
        console.log('회원가입 요청:', { email, password, agreed });
        alert('회원가입이 완료되었습니다! 로그인 페이지로 이동합니다.');
        navigate('/'); // 가입 완료 후 로그인 페이지로 이동 (필요 시 경로 수정)
    };

    return (
        <div className="login-wrapper">
            <form className="form-login" onSubmit={handleSubmit}>
                {/* 대문자 P 브랜드 로고 */}
                <div className="brand-logo mb-4">P</div>

                <h1 className="h3 mb-3 font-weight-normal">Sign Up</h1>

                {/* 개인정보 보호법 의거 약관 스크롤 박스 */}
                <div className="terms-container mb-3">
                    <label className="terms-label">개인정보 수집 및 이용 동의 (필수)</label>
                    <div className="terms-box">
                        <p><strong>[개인정보 수집 및 이용 약관]</strong></p>
                        <p>
                            본 서비스는 「개인정보 보호법」 제15조 제1항 제1호에 따라 이용자의 개인정보를 수집·이용하고자 합니다.
                        </p>
                        <p><strong>1. 수집하는 개인정보 항목</strong></p>
                        <p>- 필수항목: 이메일 주소, 비밀번호</p>
                        <p><strong>2. 개인정보의 수집 및 이용 목적</strong></p>
                        <p>- 회원 가입 의사 확인, 회원제 서비스 제공에 따른 본인 식별·인증, 회원자격 유지·관리</p>
                        <p><strong>3. 개인정보의 보유 및 이용 기간</strong></p>
                        <p>- 회원 탈퇴 시까지 (단, 관계 법령 위반에 따른 수사·조사 등이 진행 중인 경우에는 해당 수사·조사 종료 시까지)</p>
                        <p><strong>4. 동의를 거부할 권리 및 불이익</strong></p>
                        <p>- 귀하는 개인정보 수집 및 이용에 동의하지 않을 권리가 있으나, 동의 거부 시 회원가입 및 서비스 이용이 제한될 수 있습니다.</p>
                    </div>

                    <div className="terms-checkbox mt-2">
                        <label>
                            <input
                                type="checkbox"
                                checked={agreed}
                                onChange={(e) => setAgreed(e.target.checked)}
                            />{' '}
                            개인정보 수집 및 이용에 동의합니다.
                        </label>
                    </div>
                </div>

                {/* 이메일 입력 */}
                <label htmlFor="inputEmail" className="sr-only">
                    Email address
                </label>
                <input
                    type="email"
                    id="inputEmail"
                    className="form-control mb-2"
                    placeholder="Email address"
                    required
                    value={email}
                    onChange={(e) => setEmail(e.target.value)}
                />

                {/* 비밀번호 입력 */}
                <label htmlFor="inputPassword" className="sr-only">
                    Password
                </label>
                <input
                    type="password"
                    id="inputPassword"
                    className="form-control mb-2"
                    placeholder="Password"
                    required
                    value={password}
                    onChange={(e) => setPassword(e.target.value)}
                />

                {/* 비밀번호 확인 입력 */}
                <label htmlFor="confirmPassword" className="sr-only">
                    Confirm Password
                </label>
                <input
                    type="password"
                    id="confirmPassword"
                    className="form-control mb-3"
                    placeholder="Confirm Password"
                    required
                    value={confirmPassword}
                    onChange={(e) => setConfirmPassword(e.target.value)}
                />

                {/* 회원가입 버튼 */}
                <button className="btn btn-lg btn-primary btn-block mb-3" type="submit">
                    Sign Up
                </button>

                {/* 로그인 페이지로 돌아가는 링크 */}
                <div className="text-center">
                    <span style={{ fontSize: '14px', color: '#6c757d' }}>이미 계정이 있으신가요? </span>
                    <Link to="/login" className="signup-link">
                        Login
                    </Link>
                </div>

                <p className="mt-4 mb-3 text-muted">©ktaivle @bootstrap</p>
            </form>
        </div>
    );
}

export default SignupPage;