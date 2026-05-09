import { useNavigate } from 'react-router-dom';
import CompactTopBar from '../components/CompactTopBar';

export default function LocationPage() {
  const nav = useNavigate();
  return (
    <div>
      <CompactTopBar title="实时定位" onBack={() => nav(-1)} />
      <div className="p-4">
        <p className="text-sm" style={{ color: '#909399' }}>定位信息开发中...</p>
        <button className="mt-4 w-full py-2.5 rounded-xl text-white text-sm font-medium"
                style={{ background: '#165DFF' }}
                onClick={() => nav('/map')}>查看地图</button>
      </div>
    </div>
  );
}
