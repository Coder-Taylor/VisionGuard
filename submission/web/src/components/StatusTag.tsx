type Level = 'P0' | 'P1' | 'P2' | 'P3';

interface Props {
  level: Level | string;
  size?: 'sm' | 'md';
}

const colors: Record<string, { bg: string; text: string }> = {
  P0: { bg: '#F5E8E8', text: '#F53F3F' },
  P1: { bg: '#FFF0E6', text: '#FF7D00' },
  P2: { bg: '#FFF5F0', text: '#FF7D00' },
  P3: { bg: '#E8F3FF', text: '#165DFF' },
};

export default function StatusTag({ level, size = 'sm' }: Props) {
  const c = colors[level] || colors.P3;
  return (
    <span
      className={`inline-block rounded-lg font-medium ${size === 'sm' ? 'text-xs px-2 py-0.5' : 'text-sm px-3 py-1'}`}
      style={{ background: c.bg, color: c.text }}
    >
      {level}
    </span>
  );
}
