import { useNavigate } from 'react-router-dom';
import CompactTopBar from '../components/CompactTopBar';

export default function MedicationPlanPage() {
  const nav = useNavigate();
  return (
    <div>
      <CompactTopBar title="用药计划" onBack={() => nav(-1)} />
      <div className="p-4">
        <p className="text-sm" style={{ color: '#909399' }}>用药计划管理开发中...</p>
      </div>
    </div>
  );
}
