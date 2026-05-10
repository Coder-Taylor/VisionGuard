import { useState, type FormEvent } from 'react';
import { useNavigate } from 'react-router-dom';
import { useAuth } from '../context/AuthContext';

export default function RegisterPage() {
  const { register } = useAuth();
  const navigate = useNavigate();
  const [phone, setPhone] = useState('');
  const [password, setPassword] = useState('');
  const [confirmPwd, setConfirmPwd] = useState('');
  const [showPwd, setShowPwd] = useState(false);
  const [error, setError] = useState('');
  const [success, setSuccess] = useState('');
  const [loading, setLoading] = useState(false);

  async function handleSubmit(e: FormEvent) {
    e.preventDefault();
    setError('');
    setSuccess('');
    if (!phone.trim() || !password || !confirmPwd) {
      setError('请填写所有字段');
      return;
    }
    if (password.length < 8) {
      setError('密码至少 8 位');
      return;
    }
    if (password !== confirmPwd) {
      setError('两次密码不一致');
      return;
    }
    setLoading(true);
    try {
      await register({ username: phone.trim(), password, phone: phone.trim() });
      setSuccess('注册成功！即将跳转登录...');
      setTimeout(() => navigate('/login'), 1500);
    } catch (err: any) {
      setError(err.response?.data?.message || '注册失败，请重试');
    } finally {
      setLoading(false);
    }
  }

  return (
    <div className="min-h-screen flex items-center justify-center p-6"
         style={{ background: 'linear-gradient(to bottom, #FFF5F0, #FFFFFF)' }}>
      <div className="w-full max-w-sm">
        <h1 className="text-2xl font-bold text-center mb-2" style={{ color: '#165DFF' }}>VisionGuard</h1>
        <p className="text-center text-sm mb-8" style={{ color: '#666666' }}>创建新账号</p>

        <form onSubmit={handleSubmit} className="bg-white rounded-2xl p-6 shadow-sm space-y-4">
          {error && (
            <div className="text-sm text-center p-2 rounded-lg" style={{ color: '#F53F3F', background: '#F5E8E8' }}>
              {error}
            </div>
          )}
          {success && (
            <div className="text-sm text-center p-2 rounded-lg" style={{ color: '#00B42A', background: '#E6F4EA' }}>
              {success}
            </div>
          )}

          <div>
            <label className="text-sm font-medium" style={{ color: '#333333' }}>手机号</label>
            <input type="tel" className="w-full mt-1 px-3 py-2.5 rounded-xl border text-sm focus:outline-none focus:ring-2"
                   style={{ borderColor: '#e5e7eb', '--tw-ring-color': '#165DFF' } as React.CSSProperties}
                   placeholder="请输入手机号" value={phone} onChange={(e) => setPhone(e.target.value)} />
          </div>

          <div>
            <label className="text-sm font-medium" style={{ color: '#333333' }}>密码（至少 8 位）</label>
            <div className="relative mt-1">
              <input type={showPwd ? 'text' : 'password'}
                     className="w-full px-3 py-2.5 rounded-xl border text-sm pr-10 focus:outline-none focus:ring-2"
                     style={{ borderColor: '#e5e7eb', '--tw-ring-color': '#165DFF' } as React.CSSProperties}
                     placeholder="请输入密码" value={password} onChange={(e) => setPassword(e.target.value)} />
              <button type="button" className="absolute right-3 top-1/2 -translate-y-1/2 text-xs"
                      style={{ color: '#909399' }} onClick={() => setShowPwd(!showPwd)}>
                {showPwd ? '隐藏' : '显示'}
              </button>
            </div>
          </div>

          <div>
            <label className="text-sm font-medium" style={{ color: '#333333' }}>确认密码</label>
            <input type={showPwd ? 'text' : 'password'}
                   className="w-full mt-1 px-3 py-2.5 rounded-xl border text-sm focus:outline-none focus:ring-2"
                   style={{ borderColor: '#e5e7eb', '--tw-ring-color': '#165DFF' } as React.CSSProperties}
                   placeholder="请再次输入密码" value={confirmPwd} onChange={(e) => setConfirmPwd(e.target.value)} />
          </div>

          <button type="submit" disabled={loading}
                  className="w-full py-2.5 rounded-xl text-white font-medium text-sm transition-opacity disabled:opacity-60"
                  style={{ background: '#165DFF' }}>
            {loading ? '注册中...' : '注册'}
          </button>

          <div className="text-center text-xs">
            <a href="/login" style={{ color: '#165DFF' }}>已有账号？去登录</a>
          </div>
        </form>
      </div>
    </div>
  );
}
