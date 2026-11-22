import type { ButtonHTMLAttributes, ReactNode } from 'react';

const tones = {
  /* The theme's primary, as Material's Button is on the phone: white in dark, black in light. */
  key: 'bg-key text-on-key',
  /* White whatever the theme, for a pill on artwork or a mesh (DetailScreen's Play, SignInBanner). */
  white: 'bg-white text-black',
};

/**
 * The phone's filled call to action (app/.../ui/screens/DetailScreen.kt
 * ActionPill, onboarding's Next): a 50px pill, label 16px titleMedium,
 * an optional 18px glyph 8px before it. It sinks to 97% while pressed
 * (OnboardingScreen.kt), springing back as the phone's stiffness-900 spring
 * does, and dims to 40% when disabled.
 *
 *   <PrimaryPill icon={<Play />} onClick={play}>Play</PrimaryPill>
 *   <PrimaryPill tone="white" onClick={signIn}>Sign in</PrimaryPill>
 */
export function PrimaryPill({
  icon,
  tone = 'key',
  className = '',
  children,
  ...button
}: {
  icon?: ReactNode;
  tone?: keyof typeof tones;
  children: ReactNode;
} & ButtonHTMLAttributes<HTMLButtonElement>) {
  return (
    <button
      type="button"
      className={`inline-flex h-[50px] items-center justify-center gap-2 rounded-pill px-6 type-title-3-emphasized whitespace-nowrap transition-[scale] duration-150 ease-out enabled:active:scale-[.97] disabled:opacity-40 [&_svg]:size-[18px] ${tones[tone]} ${className}`}
      {...button}
    >
      {icon}
      {children}
    </button>
  );
}
