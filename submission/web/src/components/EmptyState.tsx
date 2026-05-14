export default function EmptyState({ icon, text }: { icon?: string; text: string }) {
  return (
    <div className="flex flex-col items-center justify-center py-16 gap-2">
      <span className="text-4xl opacity-40">{icon || '📭'}</span>
      <span className="text-sm" style={{ color: '#909399' }}>{text}</span>
    </div>
  );
}
