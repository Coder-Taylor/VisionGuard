import { useEffect, useState } from 'react';
import { useParams, useNavigate } from 'react-router-dom';
import CompactTopBar from '../components/CompactTopBar';
import StatusTag from '../components/StatusTag';
import LoadingSpinner from '../components/LoadingSpinner';
import ConfirmDialog from '../components/ConfirmDialog';
import { getAlertDetail, updateAlertStatus, resolveAlert } from '../api/alert';
import type { AlertDetailData } from '../types';

const ALERT_TYPE_LABEL: Record<string, string> = {
  fall: '摔倒检测', obstacle: '障碍物告警', sos: 'SOS 求救',
  heart_rate: '心率异常', low_battery: '电量过低', offline: '设备离线', geofence: '电子围栏',
};

export default function AlertDetailPage() {
  const { alertId } = useParams<{ alertId: string }>();
  const nav = useNavigate();
  const [data, setData] = useState<AlertDetailData | null>(null);
  const [loading, setLoading] = useState(true);
  const [action, setAction] = useState<string | null>(null);
  const [acting, setActing] = useState(false);

  useEffect(() => {
    if (!alertId) return;
    getAlertDetail(alertId).then(setData).catch(console.error).finally(() => setLoading(false));
  }, [alertId]);

  async function handleAction(act: string) {
    if (!alertId) return;
    setActing(true);
    try {
      if (act === 'resolve') {
        await resolveAlert(alertId, '已解决');
      } else {
        await updateAlertStatus(alertId, { action: act });
      }
      // 刷新详情
      const updated = await getAlertDetail(alertId);
      setData(updated);
    } catch (err) {
      console.error('Alert action error:', err);
    } finally {
      setActing(false);
      setAction(null);
    }
  }

  if (loading) return <div><CompactTopBar title="告警详情" onBack={() => nav(-1)} /><LoadingSpinner /></div>;
  if (!data) return <div><CompactTopBar title="告警详情" onBack={() => nav(-1)} /><p className="p-4 text-sm" style={{ color: '#909399' }}>未找到告警</p></div>;

  const canConfirm = data.status === 'pending';
  const canResolve = data.status === 'pending' || data.status === 'confirmed';
  const canClose = data.status !== 'closed';

  return (
    <div>
      <CompactTopBar title="告警详情" onBack={() => nav(-1)} />

      <div className="p-4 space-y-4">
        {/* 基本信息 */}
        <div className="bg-white rounded-2xl p-4 shadow-sm">
          <div className="flex items-center justify-between mb-3">
            <h2 className="text-lg font-semibold" style={{ color: '#333333' }}>
              {ALERT_TYPE_LABEL[data.alertType] || data.alertType}
            </h2>
            <StatusTag level={data.alertLevel} size="md" />
          </div>
          <DetailRow label="状态" value={data.status} />
          <DetailRow label="老人" value={data.elderName || '-'} />
          <DetailRow label="设备" value={data.deviceModel || data.deviceId} />
          <DetailRow label="时间" value={new Date(data.createdAt).toLocaleString('zh-CN')} />
          {data.description && <DetailRow label="描述" value={data.description} />}
        </div>

        {/* 时间线 */}
        {data.timeline && data.timeline.length > 0 && (
          <div className="bg-white rounded-2xl p-4 shadow-sm">
            <h3 className="text-sm font-semibold mb-3" style={{ color: '#333333' }}>事件时间线</h3>
            <div className="space-y-3">
              {data.timeline.map((t, i) => (
                <div key={i} className="flex gap-3">
                  <div className="flex flex-col items-center">
                    <div className="w-2 h-2 rounded-full" style={{ background: '#165DFF' }} />
                    {i < data.timeline.length - 1 && <div className="w-px flex-1 mt-1" style={{ background: '#e5e7eb' }} />}
                  </div>
                  <div className="flex-1 min-w-0 pb-2">
                    <p className="text-sm" style={{ color: '#333333' }}>{t.action}</p>
                    <p className="text-xs mt-0.5" style={{ color: '#909399' }}>
                      {new Date(t.at).toLocaleString('zh-CN')} · {t.by}
                    </p>
                  </div>
                </div>
              ))}
            </div>
          </div>
        )}

        {/* 操作按钮 */}
        <div className="flex gap-2">
          {canConfirm && (
            <button onClick={() => setAction('confirm')} disabled={acting}
                    className="flex-1 py-2.5 rounded-xl text-sm font-medium text-white"
                    style={{ background: '#165DFF' }}>确认告警</button>
          )}
          {canResolve && (
            <button onClick={() => setAction('resolve')} disabled={acting}
                    className="flex-1 py-2.5 rounded-xl text-sm font-medium text-white"
                    style={{ background: '#00B42A' }}>标记解决</button>
          )}
          {canClose && (
            <button onClick={() => setAction('close')} disabled={acting}
                    className="flex-1 py-2.5 rounded-xl text-sm font-medium"
                    style={{ color: '#666666', background: '#F5F7FA' }}>关闭告警</button>
          )}
        </div>
      </div>

      <ConfirmDialog
        open={!!action}
        title="确认操作"
        message={`确定要${action === 'confirm' ? '确认' : action === 'resolve' ? '解决' : '关闭'}此告警吗？`}
        onConfirm={() => handleAction(action!)}
        onCancel={() => setAction(null)}
      />
    </div>
  );
}

function DetailRow({ label, value }: { label: string; value: string }) {
  return (
    <div className="flex justify-between py-1.5 border-b last:border-b-0" style={{ borderColor: '#f5f5f5' }}>
      <span className="text-xs" style={{ color: '#909399' }}>{label}</span>
      <span className="text-xs text-right max-w-[60%]" style={{ color: '#333333' }}>{value}</span>
    </div>
  );
}
