import { useNavigate } from 'react-router-dom';

export default function PositionMedicinePage() {
  const nav = useNavigate();
  return (
    <div className="p-4">
      <h1 className="text-xl font-bold" style={{ color: '#333333' }}>定位用药</h1>
      <div className="grid grid-cols-2 gap-3 mt-4">
        <div className="bg-white rounded-2xl p-4 shadow-sm cursor-pointer" onClick={() => nav('/location')}
             style={{ background: '#F5F7FA' }}>
          <span className="text-2xl">📍</span>
          <p className="text-sm font-medium mt-2" style={{ color: '#333333' }}>实时定位</p>
          <p className="text-xs mt-1" style={{ color: '#909399' }}>查看老人位置</p>
        </div>
        <div className="bg-white rounded-2xl p-4 shadow-sm cursor-pointer" onClick={() => nav('/ocr')}
             style={{ background: '#F5F7FA' }}>
          <span className="text-2xl">💊</span>
          <p className="text-sm font-medium mt-2" style={{ color: '#333333' }}>用药识别</p>
          <p className="text-xs mt-1" style={{ color: '#909399' }}>OCR 药品识别</p>
        </div>
      </div>
    </div>
  );
}
