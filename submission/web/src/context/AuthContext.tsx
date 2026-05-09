import { createContext, useContext, useState, useCallback, useEffect, type ReactNode } from 'react';
import { getAccessToken, setTokens, clearTokens } from '../api/client';
import * as authApi from '../api/auth';
import type { AuthData, LoginReq, RegisterReq } from '../types';

interface AuthState {
  isLoggedIn: boolean;
  isLoading: boolean;
  userId: number | null;
  displayName: string;
  phone: string;
}

interface AuthContextType extends AuthState {
  login: (req: LoginReq) => Promise<void>;
  register: (req: RegisterReq) => Promise<void>;
  logout: () => Promise<void>;
  updateDisplayName: (name: string) => void;
}

const AuthContext = createContext<AuthContextType | null>(null);

export function AuthProvider({ children }: { children: ReactNode }) {
  const [state, setState] = useState<AuthState>({
    isLoggedIn: false,
    isLoading: true,
    userId: null,
    displayName: '',
    phone: '',
  });

  // 启动时检查 token
  useEffect(() => {
    const token = getAccessToken();
    if (token) {
      // 尝试获取用户信息
      authApi.getProfile()
        .then((profile) => {
          setState({
            isLoggedIn: true,
            isLoading: false,
            userId: profile.userId,
            displayName: profile.displayName || profile.username || '',
            phone: profile.phone || '',
          });
        })
        .catch(() => {
          clearTokens();
          setState((s) => ({ ...s, isLoading: false }));
        });
    } else {
      setState((s) => ({ ...s, isLoading: false }));
    }
  }, []);

  const login = useCallback(async (req: LoginReq) => {
    const data: AuthData = await authApi.login(req);
    setTokens(data.accessToken, data.refreshToken);
    setState({
      isLoggedIn: true,
      isLoading: false,
      userId: data.userId,
      displayName: data.displayName || '',
      phone: data.phone || '',
    });
  }, []);

  const register = useCallback(async (req: RegisterReq) => {
    await authApi.register(req);
  }, []);

  const logout = useCallback(async () => {
    try {
      const refreshToken = localStorage.getItem('vg_refresh_token');
      if (refreshToken) await authApi.logout(refreshToken);
    } catch { /* ignore */ }
    clearTokens();
    setState({
      isLoggedIn: false,
      isLoading: false,
      userId: null,
      displayName: '',
      phone: '',
    });
  }, []);

  const updateDisplayName = useCallback((name: string) => {
    setState((s) => ({ ...s, displayName: name }));
  }, []);

  return (
    <AuthContext.Provider value={{ ...state, login, register, logout, updateDisplayName }}>
      {children}
    </AuthContext.Provider>
  );
}

export function useAuth(): AuthContextType {
  const ctx = useContext(AuthContext);
  if (!ctx) throw new Error('useAuth must be inside AuthProvider');
  return ctx;
}
