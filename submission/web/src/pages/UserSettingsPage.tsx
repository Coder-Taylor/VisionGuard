import { useNavigate } from 'react-router-dom';
import CompactTopBar from '../components/CompactTopBar';

export default function UserSettingsPage() {
  const nav = useNavigate();
  return (
    <div>
      <CompactTopBar title="账号设置" onBack={() => nav(-1)} />
      <div className="p-4">
        <p className="text-sm" style={{ color: '#909399' }}>修改密码 / 手机号换绑开发中...</p>
      </div>
    </div>
  );
}
