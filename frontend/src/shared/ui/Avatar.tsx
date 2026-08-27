import { cn } from './cn'

// Avatar accepts either `name` (preferred) or `fallback` for the initials.
interface AvatarProps {
  src?: string | null
  alt?: string
  name?: string
  fallback?: string
  className?: string
  size?: 'sm' | 'md' | 'lg' | 'xl'
}

export function Avatar({ src, alt, name, fallback, className, size = 'md' }: AvatarProps) {
  const text = name ?? fallback ?? '?'
  const initials = text
    .split(' ')
    .map((w) => w[0])
    .join('')
    .slice(0, 2)
    .toUpperCase()

  // Deterministic colour from the name
  const colours = [
    'bg-blue-500', 'bg-green-500', 'bg-purple-500', 'bg-yellow-500',
    'bg-red-500', 'bg-pink-500', 'bg-indigo-500', 'bg-teal-500',
  ]
  const colour = colours[(text.charCodeAt(0) + text.charCodeAt(text.length - 1)) % colours.length]

  const sizeClasses = {
    sm: 'w-7 h-7 text-xs',
    md: 'w-9 h-9 text-sm',
    lg: 'w-12 h-12 text-base',
    xl: 'w-16 h-16 text-xl',
  }

  return (
    <div
      className={cn(
        'relative inline-flex items-center justify-center rounded-full overflow-hidden shrink-0 text-white font-medium',
        colour,
        sizeClasses[size],
        className,
      )}
    >
      {src ? (
        <img src={src} alt={alt || text} className="w-full h-full object-cover" />
      ) : (
        <span>{initials}</span>
      )}
    </div>
  )
}
