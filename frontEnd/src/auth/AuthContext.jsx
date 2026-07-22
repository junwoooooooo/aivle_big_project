/* eslint-disable react-refresh/only-export-components */

import {
  createContext,
  useContext,
  useEffect,
  useState,
} from 'react';

/* =========================================================
   저장소 키
========================================================= */

const AUTH_STORAGE_KEY =
  'persona-platform-auth';

const USERS_STORAGE_KEY =
  'persona-platform-users';

/* =========================================================
   역할
========================================================= */

export const USER_ROLE = Object.freeze({
  USER: 'USER',
  ADMIN: 'ADMIN',
});

/* =========================================================
   기본 데모 계정

   백엔드 연동 전 화면 테스트용이다.
========================================================= */

const DEFAULT_USERS = [
  {
    id: 1,
    name: '일반 사용자',
    email: 'user@test.com',
    password: 'user1234',
    role: USER_ROLE.USER,
  },
  {
    id: 2,
    name: '관리자',
    email: 'admin@test.com',
    password: 'admin1234',
    role: USER_ROLE.ADMIN,
  },
];

const AuthContext = createContext(null);

/* =========================================================
   공통 유틸리티
========================================================= */

const normalizeEmail = (email) =>
  email.trim().toLowerCase();

const createPublicUser = (user) => ({
  id: user.id,
  name: user.name,
  email: user.email,
  role: user.role,
});

const readJsonStorage = (
  storage,
  key,
  fallbackValue,
) => {
  try {
    const storedValue =
      storage.getItem(key);

    if (!storedValue) {
      return fallbackValue;
    }

    return JSON.parse(storedValue);
  } catch (error) {
    console.error(
      `${key} 저장 데이터를 읽지 못했습니다.`,
      error,
    );

    return fallbackValue;
  }
};

const writeJsonStorage = (
  storage,
  key,
  value,
) => {
  storage.setItem(
    key,
    JSON.stringify(value),
  );
};

/* =========================================================
   사용자 목록 초기화
========================================================= */

const initializeUsers = () => {
  const savedUsers = readJsonStorage(
    localStorage,
    USERS_STORAGE_KEY,
    null,
  );

  if (
    Array.isArray(savedUsers) &&
    savedUsers.length > 0
  ) {
    return savedUsers;
  }

  writeJsonStorage(
    localStorage,
    USERS_STORAGE_KEY,
    DEFAULT_USERS,
  );

  return DEFAULT_USERS;
};

/* =========================================================
   기존 로그인 복원
========================================================= */

const restoreAuthenticatedUser = () => {
  const localUser = readJsonStorage(
    localStorage,
    AUTH_STORAGE_KEY,
    null,
  );

  if (localUser) {
    return localUser;
  }

  return readJsonStorage(
    sessionStorage,
    AUTH_STORAGE_KEY,
    null,
  );
};

/* =========================================================
   AuthProvider
========================================================= */

export function AuthProvider({
  children,
}) {
  const [users, setUsers] =
    useState(() => initializeUsers());

  const [user, setUser] =
    useState(() =>
      restoreAuthenticatedUser(),
    );

  /* =======================================================
     사용자 목록 저장
  ======================================================= */

  useEffect(() => {
    writeJsonStorage(
      localStorage,
      USERS_STORAGE_KEY,
      users,
    );
  }, [users]);

  /* =======================================================
     로그인
  ======================================================= */

  const login = ({
    email,
    password,
    rememberMe = false,
  }) => {
    const normalizedEmail =
      normalizeEmail(email);

    const matchedUser = users.find(
      (currentUser) =>
        normalizeEmail(
          currentUser.email,
        ) === normalizedEmail &&
        currentUser.password === password,
    );

    if (!matchedUser) {
      return {
        success: false,
        message:
          '이메일 또는 비밀번호가 올바르지 않습니다.',
      };
    }

    const publicUser =
      createPublicUser(matchedUser);

    /*
     * 두 저장소에 인증 정보가 동시에 남지 않도록
     * 먼저 기존 정보를 제거한다.
     */

    localStorage.removeItem(
      AUTH_STORAGE_KEY,
    );

    sessionStorage.removeItem(
      AUTH_STORAGE_KEY,
    );

    const targetStorage = rememberMe
      ? localStorage
      : sessionStorage;

    writeJsonStorage(
      targetStorage,
      AUTH_STORAGE_KEY,
      publicUser,
    );

    setUser(publicUser);

    return {
      success: true,
      user: publicUser,
    };
  };

  /* =======================================================
     회원가입
  ======================================================= */

  const signup = ({
    name,
    email,
    password,
  }) => {
    const normalizedName = name.trim();
    const normalizedEmail =
      normalizeEmail(email);

    if (!normalizedName) {
      return {
        success: false,
        field: 'name',
        message:
          '이름을 입력해 주세요.',
      };
    }

    if (!normalizedEmail) {
      return {
        success: false,
        field: 'email',
        message:
          '이메일을 입력해 주세요.',
      };
    }

    const duplicatedUser = users.some(
      (currentUser) =>
        normalizeEmail(
          currentUser.email,
        ) === normalizedEmail,
    );

    if (duplicatedUser) {
      return {
        success: false,
        field: 'email',
        message:
          '이미 가입된 이메일입니다.',
      };
    }

    if (password.length < 8) {
      return {
        success: false,
        field: 'password',
        message:
          '비밀번호는 8자 이상이어야 합니다.',
      };
    }

    const newUser = {
      id: Date.now(),
      name: normalizedName,
      email: normalizedEmail,
      password,
      role: USER_ROLE.USER,
    };

    setUsers((currentUsers) => [
      ...currentUsers,
      newUser,
    ]);

    return {
      success: true,
      user: createPublicUser(newUser),
    };
  };

  /* =======================================================
     회원가입 후 즉시 로그인
  ======================================================= */

  const signupAndLogin = ({
    name,
    email,
    password,
    rememberMe = false,
  }) => {
    const signupResult = signup({
      name,
      email,
      password,
    });

    if (!signupResult.success) {
      return signupResult;
    }

    /*
     * setUsers는 비동기이므로 방금 생성한 사용자를
     * users 배열에서 다시 찾지 않고 직접 로그인 처리한다.
     */

    const publicUser =
      signupResult.user;

    localStorage.removeItem(
      AUTH_STORAGE_KEY,
    );

    sessionStorage.removeItem(
      AUTH_STORAGE_KEY,
    );

    const targetStorage = rememberMe
      ? localStorage
      : sessionStorage;

    writeJsonStorage(
      targetStorage,
      AUTH_STORAGE_KEY,
      publicUser,
    );

    setUser(publicUser);

    return {
      success: true,
      user: publicUser,
    };
  };

  /* =======================================================
     로그아웃
  ======================================================= */

  const logout = () => {
    localStorage.removeItem(
      AUTH_STORAGE_KEY,
    );

    sessionStorage.removeItem(
      AUTH_STORAGE_KEY,
    );

    setUser(null);
  };

  const contextValue = {
    user,
    users,

    isAuthenticated: Boolean(user),

    isAdmin:
      user?.role === USER_ROLE.ADMIN,

    login,
    signup,
    signupAndLogin,
    logout,
  };

  return (
    <AuthContext.Provider
      value={contextValue}
    >
      {children}
    </AuthContext.Provider>
  );
}

/* =========================================================
   인증 컨텍스트 훅
========================================================= */

export function useAuth() {
  const context =
    useContext(AuthContext);

  if (!context) {
    throw new Error(
      'useAuth는 AuthProvider 내부에서 사용해야 합니다.',
    );
  }

  return context;
}
