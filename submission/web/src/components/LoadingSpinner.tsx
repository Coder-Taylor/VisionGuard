export default function LoadingSpinner({ text }: { text?: string }) {
  return (
    <div className="flex flex-col items-center justify-center py-12 gap-3">
      <div className="w-8 h-8 border-2 rounded-full animate-spin"
           style={{ borderColor: '#165DFF', borderTopColor: 'transparent' }} />
      {text && <span className="text-sm" style={{ color: '#666666' }}>{text}</span>}
    </div>
  );
}
