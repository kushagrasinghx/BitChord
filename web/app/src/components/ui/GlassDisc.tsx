import type { ButtonHTMLAttributes, ReactNode } from 'react';

const variants = {
  /*
   * The player's star and ⋯ (sharedUi/.../ui/player/PlayerControls.kt
   * CircleGlyph in GlassDiscPair): 34px, white at 18% over a blurred
   * #1C1C1E slab (glassFrost), brightened to 34% while active so the state
   * shows on any sleeve; a 19px white glyph.
   */
  disc: 'size-[34px] bg-[#1c1c1e61] bg-[linear-gradient(#ffffff2e,#ffffff2e)] text-white backdrop-blur-[20px] aria-pressed:bg-[linear-gradient(#ffffff57,#ffffff57)] focus-visible:bg-[linear-gradient(#ffffff57,#ffffff57)] [&_svg]:size-[19px]',
  /*
   * A pushed page's back button (app/.../ui/components/FrostedTopBar.kt
   * ArtworkPageBackButton): 44px of the bar's frosted surface with its 10%
   * white hairline, the glyph in the page's text colour. On an artwork-tinted
   * page that text colour is chosen for the tint, not for the theme's glass
   * (white on a dark sleeve under the light theme's pale glass), so the
   * circle takes the tint itself, nudged an eighth towards the glyph so it
   * still reads as a control, with a hairline from the tint's own palette.
   */
  back: 'size-11 glass-material text-primary shadow-[inset_0_0_0_0.5px_#ffffff1a] in-data-tint:bg-[color-mix(in_srgb,var(--page-tint)_88%,var(--systemPrimary))] in-data-tint:shadow-[inset_0_0_0_0.5px_var(--systemQuaternary)]',
};

/**
 * A round glass button holding one glyph. `disc` carries the glass rim, a
 * hairline brighter along the top where the light would catch it
 * (PlayerControls.kt glassRim: white .42 to .14).
 *
 *   <GlassDisc label="Love" active={liked} onClick={toggleLike}><Star /></GlassDisc>
 *   <GlassDisc variant="back" label="Back" onClick={() => navigate(-1)}><ChevronLeft /></GlassDisc>
 */
export function GlassDisc({
  label,
  variant = 'disc',
  active,
  className = '',
  children,
  ...button
}: {
  /** What it does, for assistive tech: the glyph is all that shows. */
  label: string;
  variant?: keyof typeof variants;
  /** A toggle's state (aria-pressed); the disc brightens. Leave out for a plain button. */
  active?: boolean;
  children: ReactNode;
} & ButtonHTMLAttributes<HTMLButtonElement>) {
  return (
    <button
      type="button"
      aria-label={label}
      aria-pressed={active}
      className={[
        'relative grid flex-none place-items-center rounded-full transition-[background-color,background-image] duration-150 disabled:opacity-40',
        variants[variant],
        variant === 'disc' &&
          "after:pointer-events-none after:absolute after:inset-0 after:rounded-full after:bg-[linear-gradient(#ffffff6b,#ffffff24)] after:p-px after:content-[''] after:[mask:linear-gradient(#000_0_0)_content-box_exclude,linear-gradient(#000_0_0)]",
        className,
      ]
        .filter(Boolean)
        .join(' ')}
      {...button}
    >
      {children}
    </button>
  );
}
