export default function UnreadBadge({ count }: { count: number }) {
  if (count <= 0) return null;
  return (
    <span className="inline-flex items-center justify-center w-5 h-5 rounded-full text-white text-xs animate-pulse"
          style={{ background: '#F53F3F' }}>
      {count > 99 ? '99+' : count}
    </span>
  );
}
