import { useNavigate } from 'react-router-dom';
import { useAuth } from '../context/AuthContext';

export default function ProfilePage() {
  const { displayName, phone, logout } = useAuth();
  const nav = useNavigate();

  const menus = [
    { label: '我的老人', path: '/elders', icon: '👴' },
    { label: '设备管理', path: '/devices', icon: '📱' },
    { label: '消息通知', path: '/notifications', icon: '💬' },
    { label: '账号设置', path: '/settings', icon: '⚙️' },
  ];

  return (
    <div className="p-4">
      {/* 用户信息卡片 */}
      <div className="bg-white rounded-2xl p-4 shadow-sm flex items-center gap-3">
        <div className="w-12 h-12 rounded-full flex items-center justify-center text-white font-bold text-lg"
             style={{ background: '#165DFF' }}>
          {displayName?.[0] || 'U'}
        </div>
        <div>
          <p className="font-semibold" style={{ color: '#333333' }}>{displayName || '用户'}</p>
          <p className="text-xs" style={{ color: '#909399' }}>{phone || ''}</p>
        </div>
      </div>

      {/* 菜单 */}
      <div className="bg-white rounded-2xl mt-3 shadow-sm overflow-hidden">
        {menus.map((m) => (
          <div key={m.path} className="flex items-center gap-3 px-4 py-3 cursor-pointer hover:bg-gray-50 border-b last:border-b-0"
               onClick={() => nav(m.path)} style={{ borderColor: '#f5f5f5' }}>
            <span className="text-lg">{m.icon}</span>
            <span className="flex-1 text-sm" style={{ color: '#333333' }}>{m.label}</span>
            <span style={{ color: '#ccc' }}>›</span>
          </div>
        ))}
      </div>

      {/* 退出登录 */}
      <button onClick={logout}
              className="w-full mt-6 py-2.5 rounded-xl text-sm font-medium"
              style={{ color: '#F53F3F', background: '#F5E8E8' }}>
        退出登录
      </button>
    </div>
  );
}
