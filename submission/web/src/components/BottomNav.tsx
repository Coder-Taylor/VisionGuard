import { useLocation, useNavigate } from 'react-router-dom';

const tabs = [
  { path: '/home', label: '首页', icon: '🏠' },
  { path: '/position-medicine', label: '定位用药', icon: '📍' },
  { path: '/alerts', label: '告警历史', icon: '🔔' },
  { path: '/profile', label: '个人中心', icon: '👤' },
];

export default function BottomNav() {
  const location = useLocation();
  const navigate = useNavigate();

  // 子页面不显示底部导航
  const isSubPage = !tabs.some((t) => t.path === location.pathname);
  if (isSubPage) return null;

  return (
    <nav className="fixed bottom-0 left-0 right-0 bg-white border-t flex z-40"
         style={{ borderColor: '#e5e7eb' }}>
      {tabs.map((tab) => {
        const active = location.pathname === tab.path;
        return (
          <button
            key={tab.path}
            onClick={() => navigate(tab.path)}
            className="flex-1 flex flex-col items-center justify-center py-2 gap-0.5 transition-colors"
            style={{ color: active ? '#165DFF' : '#909399' }}
          >
            <span className="text-lg">{tab.icon}</span>
            <span className="text-xs font-medium">{tab.label}</span>
          </button>
        );
      })}
    </nav>
  );
}
