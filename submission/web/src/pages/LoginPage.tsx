import { useState, FormEvent } from 'react';
import { useAuth } from '../context/AuthContext';

export default function LoginPage() {
  const { login } = useAuth();
  const [phone, setPhone] = useState('');
  const [password, setPassword] = useState('');
  const [showPwd, setShowPwd] = useState(false);
  const [error, setError] = useState('');
  const [loading, setLoading] = useState(false);

  async function handleSubmit(e: FormEvent) {
    e.preventDefault();
    setError('');
    if (!phone.trim() || !password) {
      setError('请输入手机号和密码');
      return;
    }
    setLoading(true);
    try {
      await login({ username: phone.trim(), password });
    } catch (err: any) {
      const msg = err.response?.data?.message || err.message || '登录失败，请重试';
      setError(msg);
    } finally {
      setLoading(false);
    }
  }

  return (
    <div className="min-h-screen flex items-center justify-center p-6"
         style={{ background: 'linear-gradient(to bottom, #FFF5F0, #FFFFFF)' }}>
      <div className="w-full max-w-sm">
        <h1 className="text-2xl font-bold text-center mb-2" style={{ color: '#165DFF' }}>VisionGuard</h1>
        <p className="text-center text-sm mb-8" style={{ color: '#666666' }}>智能守护 · 视障与老人关爱系统</p>

        <form onSubmit={handleSubmit} className="bg-white rounded-2xl p-6 shadow-sm space-y-4">
          <h2 className="text-lg font-semibold text-center" style={{ color: '#333333' }}>登录</h2>

          {error && (
            <div className="text-sm text-center p-2 rounded-lg" style={{ color: '#F53F3F', background: '#F5E8E8' }}>
              {error}
            </div>
          )}

          <div>
            <label className="text-sm font-medium" style={{ color: '#333333' }}>手机号</label>
            <input
              type="tel"
              className="w-full mt-1 px-3 py-2.5 rounded-xl border text-sm focus:outline-none focus:ring-2"
              style={{ borderColor: '#e5e7eb', '--tw-ring-color': '#165DFF' } as React.CSSProperties}
              placeholder="请输入手机号"
              value={phone}
              onChange={(e) => setPhone(e.target.value)}
            />
          </div>

          <div>
            <label className="text-sm font-medium" style={{ color: '#333333' }}>密码</label>
            <div className="relative mt-1">
              <input
                type={showPwd ? 'text' : 'password'}
                className="w-full px-3 py-2.5 rounded-xl border text-sm pr-10 focus:outline-none focus:ring-2"
                style={{ borderColor: '#e5e7eb', '--tw-ring-color': '#165DFF' } as React.CSSProperties}
                placeholder="请输入密码"
                value={password}
                onChange={(e) => setPassword(e.target.value)}
              />
              <button type="button" className="absolute right-3 top-1/2 -translate-y-1/2 text-xs"
                      style={{ color: '#909399' }}
                      onClick={() => setShowPwd(!showPwd)}>
                {showPwd ? '隐藏' : '显示'}
              </button>
            </div>
          </div>

          <button
            type="submit"
            disabled={loading}
            className="w-full py-2.5 rounded-xl text-white font-medium text-sm transition-opacity disabled:opacity-60"
            style={{ background: '#165DFF' }}
          >
            {loading ? '登录中...' : '登录'}
          </button>

          <div className="flex justify-between text-xs">
            <a href="/register" style={{ color: '#165DFF' }}>注册账号</a>
            <a href="/forgot-password" style={{ color: '#909399' }}>忘记密码</a>
          </div>
        </form>
      </div>
    </div>
  );
}
