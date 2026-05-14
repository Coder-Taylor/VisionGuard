import { useEffect, useState, useCallback } from 'react';
import { useNavigate } from 'react-router-dom';
import { getDashboard } from '../api/elder';
import { listAlerts, updateAlertStatus } from '../api/alert';
import type { DashboardData, AlertData } from '../types';
import StatusTag from '../components/StatusTag';
import LoadingSpinner from '../components/LoadingSpinner';

export default function HomePage() {
  const nav = useNavigate();
  const [dash, setDash] = useState<DashboardData | null>(null);
  const [alerts, setAlerts] = useState<AlertData[]>([]);
  const [loading, setLoading] = useState(true);
  const [dismissedIds, setDismissedIds] = useState<Set<string>>(new Set());

  const fetchData = useCallback(async () => {
    try {
      const [d, a] = await Promise.all([
        getDashboard(),
        listAlerts({ pageSize: 10 }),
      ]);
      setDash(d);
      setAlerts(a.list || []);
    } catch (err) {
      console.error('Home fetch error:', err);
    } finally {
      setLoading(false);
    }
  }, []);

  useEffect(() => { fetchData(); }, [fetchData]);

  async function handleDismiss(alertId: string) {
    try {
      await updateAlertStatus(alertId, { action: 'confirm' });
      setDismissedIds((prev) => new Set(prev).add(alertId));
    } catch { /* ignore */ }
  }

  async function handleDismissAll() {
    const pending = alerts.filter((a) => a.status === 'pending' && !dismissedIds.has(a.alertId));
    for (const a of pending) {
      try { await updateAlertStatus(a.alertId, { action: 'confirm' }); } catch { /* skip */ }
    }
    setDismissedIds((prev) => {
      const next = new Set(prev);
      pending.forEach((a) => next.add(a.alertId));
      return next;
    });
  }

  if (loading) return <LoadingSpinner text="加载中..." />;

  const pendingAlerts = alerts.filter((a) => a.status === 'pending' && !dismissedIds.has(a.alertId));
  const isAlert = pendingAlerts.length > 0;

  return (
    <div className="p-4">
      {/* 安全状态 */}
      <div className="rounded-2xl p-4 mb-4" style={{ background: isAlert ? '#F5E8E8' : '#E6F4EA' }}>
        <div className="flex items-center gap-3">
          <span className="text-2xl">{isAlert ? '🔴' : '🟢'}</span>
          <div>
            <p className="font-semibold text-lg" style={{ color: isAlert ? '#F53F3F' : '#00B42A' }}>
              {isAlert ? '有待处理告警' : '一切正常'}
            </p>
            <p className="text-xs" style={{ color: isAlert ? '#F53F3F' : '#666666' }}>
              {isAlert ? `${pendingAlerts.length} 条待处理` : '老人状态安全'}
            </p>
          </div>
        </div>
      </div>

      {/* 统计卡片 */}
      <div className="grid grid-cols-3 gap-3 mb-4">
        <StatCard label="监护老人" value={dash?.elderCount ?? 0} color="#165DFF" bg="#E8F3FF" />
        <StatCard label="在线设备" value={dash?.onlineDeviceCount ?? 0} color="#00B42A" bg="#E6F4EA" />
        <StatCard label="24h 告警" value={dash?.alertCount24h ?? 0} color="#FF7D00" bg="#FFF0E6" />
      </div>

      {/* 一键忽视 */}
      {pendingAlerts.length >= 2 && (
        <button onClick={handleDismissAll}
                className="w-full py-2.5 rounded-xl text-white text-sm font-medium mb-4"
                style={{ background: '#165DFF' }}>
          一键忽视（{pendingAlerts.length} 条）
        </button>
      )}

      {/* 最近告警 */}
      <h2 className="text-base font-semibold mb-3" style={{ color: '#333333' }}>最近告警</h2>
      {alerts.length === 0 ? (
        <p className="text-center text-sm py-8" style={{ color: '#909399' }}>暂无告警记录</p>
      ) : (
        <div className="space-y-2">
          {alerts.slice(0, 5).map((a) => {
            const dismissed = dismissedIds.has(a.alertId);
            return (
              <div key={a.alertId}
                   className="bg-white rounded-2xl p-3 shadow-sm cursor-pointer flex items-center gap-3"
                   onClick={() => nav(`/alerts/${a.alertId}`)}>
                <div className="flex-1 min-w-0">
                  <div className="flex items-center gap-2">
                    <span className="text-sm font-medium" style={{ color: dismissed ? '#909399' : '#333333' }}>
                      {ALERT_TYPE_LABEL[a.alertType] || a.alertType}
                    </span>
                    <StatusTag level={a.alertLevel} />
                  </div>
                  <p className="text-xs mt-1 truncate" style={{ color: '#909399' }}>
                    {a.description || '-'}
                  </p>
                </div>
                {a.status === 'pending' && !dismissed && (
                  <button onClick={(e) => { e.stopPropagation(); handleDismiss(a.alertId); }}
                          className="text-xs px-3 py-1 rounded-lg font-medium"
                          style={{ color: '#165DFF', background: '#E8F3FF' }}>
                    忽视
                  </button>
                )}
                {dismissed && <span className="text-xs" style={{ color: '#909399' }}>已忽视</span>}
              </div>
            );
          })}
        </div>
      )}
    </div>
  );
}

function StatCard({ label, value, color, bg }: { label: string; value: number; color: string; bg: string }) {
  return (
    <div className="rounded-2xl p-3 text-center" style={{ background: bg }}>
      <p className="text-2xl font-bold" style={{ color }}>{value}</p>
      <p className="text-xs mt-1" style={{ color: '#666666' }}>{label}</p>
    </div>
  );
}

const ALERT_TYPE_LABEL: Record<string, string> = {
  fall: '摔倒检测',
  obstacle: '障碍物告警',
  sos: 'SOS 求救',
  heart_rate: '心率异常',
  low_battery: '电量过低',
  offline: '设备离线',
  geofence: '电子围栏',
};
