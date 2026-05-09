import { useParams, useNavigate } from 'react-router-dom';
import CompactTopBar from '../components/CompactTopBar';

export default function ElderDetailPage() {
  const { elderId } = useParams();
  const nav = useNavigate();
  return (
    <div>
      <CompactTopBar title="老人详情" onBack={() => nav(-1)} />
      <div className="p-4">
        <p className="text-sm" style={{ color: '#909399' }}>老人 ID: {elderId}</p>
        <p className="text-sm mt-2" style={{ color: '#909399' }}>详情页开发中...</p>
      </div>
    </div>
  );
}
