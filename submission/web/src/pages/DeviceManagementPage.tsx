import { useNavigate } from 'react-router-dom';
import CompactTopBar from '../components/CompactTopBar';

export default function DeviceManagementPage() {
  const nav = useNavigate();
  return (
    <div>
      <CompactTopBar title="设备管理" onBack={() => nav(-1)} />
      <div className="p-4">
        <p className="text-sm" style={{ color: '#909399' }}>设备列表开发中...</p>
      </div>
    </div>
  );
}
