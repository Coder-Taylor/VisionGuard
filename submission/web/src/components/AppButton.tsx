type Variant = 'primary' | 'secondary' | 'danger';

interface Props {
  children: React.ReactNode;
  variant?: Variant;
  onClick?: () => void;
  disabled?: boolean;
  className?: string;
  type?: 'button' | 'submit';
}

const styles: Record<Variant, React.CSSProperties> = {
  primary: { background: '#165DFF', color: '#fff' },
  secondary: { background: '#F5F7FA', color: '#333333' },
  danger: { background: '#F53F3F', color: '#fff' },
};

export default function AppButton({ children, variant = 'primary', onClick, disabled, className = '', type = 'button' }: Props) {
  return (
    <button
      type={type}
      onClick={onClick}
      disabled={disabled}
      className={`px-4 py-2.5 rounded-xl font-medium text-sm transition-opacity disabled:opacity-50 ${className}`}
      style={styles[variant]}
    >
      {children}
    </button>
  );
}
