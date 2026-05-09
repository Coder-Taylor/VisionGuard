import { useEffect, useState, useCallback } from 'react';
import { useNavigate } from 'react-router-dom';
import { listAlerts } from '../api/alert';
import type { AlertData, PaginatedData } from '../types';
import StatusTag from '../components/StatusTag';
import LoadingSpinner from '../components/LoadingSpinner';
import EmptyState from '../components/EmptyState';

const ALERT_TYPE_LABEL: Record<string, string> = {
  fall: '摔倒检测', obstacle: '障碍物告警', sos: 'SOS 求救',
  heart_rate: '心率异常', low_battery: '电量过低', offline: '设备离线', geofence: '电子围栏',
};

const STATUS_LABEL: Record<string, { label: string; color: string }> = {
  pending: { label: '待处理', color: '#F53F3F' },
  confirmed: { label: '已确认', color: '#FF7D00' },
  resolved: { label: '已解决', color: '#00B42A' },
  closed: { label: '已关闭', color: '#909399' },
};

export default function AlertHistoryPage() {
  const nav = useNavigate();
  const [data, setData] = useState<PaginatedData<AlertData> | null>(null);
  const [loading, setLoading] = useState(true);
  const [page, setPage] = useState(1);

  const fetchData = useCallback(async () => {
    setLoading(true);
    try {
      const res = await listAlerts({ page, pageSize: 20 });
      setData(res);
    } catch (err) {
      console.error('Alert list error:', err);
    } finally {
      setLoading(false);
    }
  }, [page]);

  useEffect(() => { fetchData(); }, [fetchData]);

  const alerts = data?.list || [];

  return (
    <div className="p-4">
      <h1 className="text-xl font-bold mb-4" style={{ color: '#333333' }}>告警历史</h1>

      {loading ? <LoadingSpinner /> : alerts.length === 0 ? <EmptyState text="暂无告警记录" /> : (
        <div className="space-y-2">
          {alerts.map((a) => (
            <div key={a.alertId}
                 className="bg-white rounded-2xl p-3 shadow-sm cursor-pointer"
                 onClick={() => nav(`/alerts/${a.alertId}`)}>
              <div className="flex items-center justify-between">
                <div className="flex items-center gap-2">
                  <span className="text-sm font-medium" style={{ color: '#333333' }}>
                    {ALERT_TYPE_LABEL[a.alertType] || a.alertType}
                  </span>
                  <StatusTag level={a.alertLevel} />
                </div>
                <span className="text-xs px-2 py-0.5 rounded-lg font-medium"
                      style={{ color: STATUS_LABEL[a.status]?.color || '#909399' }}>
                  {STATUS_LABEL[a.status]?.label || a.status}
                </span>
              </div>
              {a.description && (
                <p className="text-xs mt-1 truncate" style={{ color: '#909399' }}>{a.description}</p>
              )}
              <p className="text-xs mt-1" style={{ color: '#ccc' }}>
                {new Date(a.createdAt).toLocaleString('zh-CN')}
              </p>
            </div>
          ))}

          {/* 分页 */}
          {data && data.total > 20 && (
            <div className="flex justify-center gap-4 pt-4">
              <button disabled={page <= 1} onClick={() => setPage(page - 1)}
                      className="text-sm px-4 py-2 rounded-xl disabled:opacity-30"
                      style={{ color: '#165DFF', background: '#E8F3FF' }}>上一页</button>
              <span className="text-sm self-center" style={{ color: '#666666' }}>
                {page} / {Math.ceil(data.total / data.pageSize)}
              </span>
              <button disabled={page >= Math.ceil(data.total / data.pageSize)}
                      onClick={() => setPage(page + 1)}
                      className="text-sm px-4 py-2 rounded-xl disabled:opacity-30"
                      style={{ color: '#165DFF', background: '#E8F3FF' }}>下一页</button>
            </div>
          )}
        </div>
      )}
    </div>
  );
}
