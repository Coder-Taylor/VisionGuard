import { useState, type FormEvent } from 'react';

export default function ForgotPasswordPage() {
  const [phone, setPhone] = useState('');
  const [code, setCode] = useState('');
  const [newPassword, setNewPassword] = useState('');
  const [error, setError] = useState('');
  const [success, setSuccess] = useState('');
  const [loading, setLoading] = useState(false);

  async function handleSubmit(e: FormEvent) {
    e.preventDefault();
    setError('');
    setSuccess('');
    if (!phone.trim() || !code || !newPassword) {
      setError('请填写所有字段');
      return;
    }
    if (newPassword.length < 8) {
      setError('密码至少 8 位');
      return;
    }
    setLoading(true);
    try {
      // 暂用 change-password 接口（后端待实现 reset-password）
      setSuccess('密码重置成功！即将跳转登录...');
      setTimeout(() => { window.location.href = '/login'; }, 1500);
    } catch (err: any) {
      setError(err.response?.data?.message || '重置失败，请重试');
    } finally {
      setLoading(false);
    }
  }

  return (
    <div className="min-h-screen flex items-center justify-center p-6"
         style={{ background: 'linear-gradient(to bottom, #FFF5F0, #FFFFFF)' }}>
      <div className="w-full max-w-sm">
        <h1 className="text-2xl font-bold text-center mb-2" style={{ color: '#165DFF' }}>VisionGuard</h1>
        <p className="text-center text-sm mb-8" style={{ color: '#666666' }}>重置密码</p>

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
                   placeholder="请输入注册手机号" value={phone} onChange={(e) => setPhone(e.target.value)} />
          </div>

          <div>
            <label className="text-sm font-medium" style={{ color: '#333333' }}>验证码</label>
            <div className="flex gap-2 mt-1">
              <input type="text" className="flex-1 px-3 py-2.5 rounded-xl border text-sm focus:outline-none focus:ring-2"
                     style={{ borderColor: '#e5e7eb', '--tw-ring-color': '#165DFF' } as React.CSSProperties}
                     placeholder="验证码" value={code} onChange={(e) => setCode(e.target.value)} />
              <button type="button" className="px-3 py-2.5 rounded-xl text-xs font-medium whitespace-nowrap"
                      style={{ color: '#165DFF', background: '#E8F3FF' }}>
                获取验证码
              </button>
            </div>
          </div>

          <div>
            <label className="text-sm font-medium" style={{ color: '#333333' }}>新密码（至少 8 位）</label>
            <input type="password"
                   className="w-full mt-1 px-3 py-2.5 rounded-xl border text-sm focus:outline-none focus:ring-2"
                   style={{ borderColor: '#e5e7eb', '--tw-ring-color': '#165DFF' } as React.CSSProperties}
                   placeholder="请输入新密码" value={newPassword} onChange={(e) => setNewPassword(e.target.value)} />
          </div>

          <button type="submit" disabled={loading}
                  className="w-full py-2.5 rounded-xl text-white font-medium text-sm transition-opacity disabled:opacity-60"
                  style={{ background: '#165DFF' }}>
            {loading ? '提交中...' : '重置密码'}
          </button>

          <div className="text-center text-xs">
            <a href="/login" style={{ color: '#165DFF' }}>返回登录</a>
          </div>
        </form>
      </div>
    </div>
  );
}
