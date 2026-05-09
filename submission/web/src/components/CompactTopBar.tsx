interface Props {
  title: string;
  onBack: () => void;
  action?: React.ReactNode;
}

export default function CompactTopBar({ title, onBack, action }: Props) {
  return (
    <div className="flex items-center h-12 px-2" style={{ background: '#165DFF' }}>
      <button onClick={onBack} className="p-2 text-white">
        <svg width="20" height="20" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2">
          <path d="M15 18l-6-6 6-6" />
        </svg>
      </button>
      <span className="flex-1 text-white font-medium text-base ml-1">{title}</span>
      {action}
    </div>
  );
}
