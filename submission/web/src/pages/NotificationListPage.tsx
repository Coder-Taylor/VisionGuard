import { useNavigate } from 'react-router-dom';
import CompactTopBar from '../components/CompactTopBar';

export default function NotificationListPage() {
  const nav = useNavigate();
  return (
    <div>
      <CompactTopBar title="消息通知" onBack={() => nav(-1)} />
      <div className="p-4">
        <p className="text-sm" style={{ color: '#909399' }}>通知列表开发中...</p>
      </div>
    </div>
  );
}
