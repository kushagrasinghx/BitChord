import { X } from 'lucide-react';
import type { ReactNode } from 'react';

interface BannerProps {
  title: string;
  detail: string;
  action: ReactNode;
  onDismiss?: () => void;
}

/**
 * The full-width band pinned under the player for signed-out visitors. An
 * invitation, not an error, so neutral: the phone's surfaceVariant with a
 * key-colour button, as its sign-in prompts are monochrome.
 */
export function Banner({ title, detail, action, onDismiss }: BannerProps) {
  return (
    <div
      className="relative z-(--z-web-chrome) flex min-h-[71px] items-center gap-4 bg-surface-variant px-[30px] py-3 text-primary max-sm:pr-4 max-sm:pl-5"
      role="region"
      aria-label={title}
    >
      <div className="min-w-0 flex-1">
        <p className="type-title-2-emphasized max-sm:type-[700_15px/20px]">{title}</p>
        <p className="mt-0.5 type-callout-medium text-secondary">{detail}</p>
      </div>
      <div className="shrink-0">{action}</div>
      {onDismiss && (
        <button
          className="-mr-3.5 grid size-7 place-items-center rounded-full text-secondary hover:bg-quaternary hover:text-primary"
          onClick={onDismiss}
          aria-label="Dismiss"
        >
          <X size={16} strokeWidth={2.4} />
        </button>
      )}
    </div>
  );
}

export const bannerButtonClass =
  'inline-flex h-8 items-center whitespace-nowrap rounded-pill bg-key px-4 type-body-emphasized text-on-key hover:bg-key-rollover';
