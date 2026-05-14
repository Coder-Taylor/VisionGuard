import { useNavigate } from 'react-router-dom';
import CompactTopBar from '../components/CompactTopBar';

export default function OcrMedicinePage() {
  const nav = useNavigate();
  return (
    <div>
      <CompactTopBar title="用药识别" onBack={() => nav(-1)} />
      <div className="p-4">
        <p className="text-sm" style={{ color: '#909399' }}>OCR 识别记录列表开发中...</p>
        <button className="mt-4 w-full py-2.5 rounded-xl text-white text-sm font-medium"
                style={{ background: '#165DFF' }}
                onClick={() => nav('/medication')}>用药计划</button>
      </div>
    </div>
  );
}
