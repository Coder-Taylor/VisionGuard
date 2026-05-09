interface Props {
  open: boolean;
  title: string;
  message: string;
  confirmText?: string;
  cancelText?: string;
  danger?: boolean;
  onConfirm: () => void;
  onCancel: () => void;
}

export default function ConfirmDialog({ open, title, message, confirmText = '确认', cancelText = '取消', danger, onConfirm, onCancel }: Props) {
  if (!open) return null;
  return (
    <div className="fixed inset-0 z-50 flex items-center justify-center bg-black/40" onClick={onCancel}>
      <div className="bg-white rounded-2xl p-6 mx-6 w-full max-w-sm shadow-xl" onClick={(e) => e.stopPropagation()}>
        <h3 className="text-lg font-semibold" style={{ color: '#333333' }}>{title}</h3>
        <p className="text-sm mt-2" style={{ color: '#666666' }}>{message}</p>
        <div className="flex gap-3 mt-6">
          <button className="flex-1 py-2.5 rounded-xl text-sm font-medium" style={{ background: '#F5F7FA', color: '#666666' }}
                  onClick={onCancel}>{cancelText}</button>
          <button className="flex-1 py-2.5 rounded-xl text-sm font-medium text-white"
                  style={{ background: danger ? '#F53F3F' : '#165DFF' }}
                  onClick={onConfirm}>{confirmText}</button>
        </div>
      </div>
    </div>
  );
}
