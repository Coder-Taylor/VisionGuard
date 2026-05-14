import { useNavigate } from 'react-router-dom';
import CompactTopBar from '../components/CompactTopBar';

export default function MapPage() {
  const nav = useNavigate();
  return (
    <div>
      <CompactTopBar title="地图" onBack={() => nav(-1)} />
      <div className="p-4">
        <p className="text-sm" style={{ color: '#909399' }}>高德地图加载中...</p>
      </div>
    </div>
  );
}
