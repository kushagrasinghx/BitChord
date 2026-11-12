/**
 * The boxed "E" (report §2), 4px after the title. Drawn as one shape with the
 * letter cut out of it, so the E sits exactly in the middle at any size and
 * shows whatever is behind it (a tinted page, the full-screen mesh).
 */
export function ExplicitBadge({ size = 12 }: { size?: number }) {
  return (
    <svg
      className="ms-1 inline-block shrink-0 align-[-1px] text-secondary"
      width={size}
      height={size}
      viewBox="0 0 12 12"
      role="img"
      aria-label="Explicit"
    >
      <path
        fill="currentColor"
        fillRule="evenodd"
        d="M2.5 0h7A2.5 2.5 0 0 1 12 2.5v7A2.5 2.5 0 0 1 9.5 12h-7A2.5 2.5 0 0 1 0 9.5v-7A2.5 2.5 0 0 1 2.5 0ZM3.9 3v6h4.2V7.75H5.35v-1.15H7.8V5.4H5.35V4.25H8.1V3Z"
      />
    </svg>
  );
}
