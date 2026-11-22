/**
 * The phone's switch (app/src/main/java/com/music/bitchord/ui/components/
 * IosSwitch.kt): a 64×28 capsule, iOS green when on, and a 40×24 white pill
 * of a thumb that stretches toward where it is going while pressed.
 *
 * A real checkbox (role="switch") under the drawing, so a <label> around the
 * row toggles it, as the settings rows do, and the keyboard works as for any
 * checkbox.
 *
 *   <label className={settingsUi.controlRow}>
 *     <span>AutoPlay</span>
 *     <Switch checked={autoplay} onChange={setAutoplay} />
 *   </label>
 */
export function Switch({
  checked,
  onChange,
  disabled,
  label,
}: {
  checked: boolean;
  onChange: (on: boolean) => void;
  disabled?: boolean;
  /** Only when nothing labels it already (no <label> around it). */
  label?: string;
}) {
  return (
    <span className="relative inline-flex h-7 w-16 flex-none has-disabled:opacity-45">
      <input
        type="checkbox"
        role="switch"
        className="peer absolute inset-0 z-1 m-0 cursor-pointer appearance-none rounded-pill disabled:cursor-default"
        checked={checked}
        disabled={disabled}
        aria-label={label}
        onChange={(e) => onChange(e.currentTarget.checked)}
      />
      {/* The track's colour eases as Compose's animateColorAsState does. */}
      <span className="absolute inset-0 rounded-pill bg-switch-off transition-colors duration-200 peer-checked:bg-switch-on" />
      {/*
       * The thumb: slid on the phone's spring (damping .78, stiffness 700),
       * and stretched 1.18× on another (.7, 900) from the edge it is leaving.
       */}
      <span
        className={[
          'absolute top-0.5 left-0.5 h-6 w-10 origin-left rounded-pill bg-white shadow-[0_1px_2px_rgb(0_0_0/0.3)]',
          'transition-[translate,scale] duration-[301ms,296ms]',
          'ease-[linear(0,0.12,0.353,0.584,0.766,0.89,0.964,1.002,1.017,1.02,1.017,1.012,1.008,1.004,1),linear(0,0.191,0.523,0.796,0.959,1.031,1.046,1.036,1.021,1.009,1.002,0.999,1)]',
          'peer-checked:origin-right peer-checked:translate-x-5 peer-active:scale-x-[1.18]',
          'rtl:right-0.5 rtl:left-auto rtl:origin-right rtl:peer-checked:origin-left rtl:peer-checked:-translate-x-5',
        ].join(' ')}
      />
    </span>
  );
}
