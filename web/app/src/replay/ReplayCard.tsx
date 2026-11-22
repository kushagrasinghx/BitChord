import './replay.css';
import { LogoMark } from '@/components/icons/LogoMark';
import { PaletteMesh, useArtworkMesh } from './PaletteMesh';

const emboss = 'replay-emboss truncate font-[ui-monospace,SFMono-Regular,Menlo,Consolas,monospace] font-semibold tracking-[1.6px] uppercase';

/**
 * One headline number as a card you could keep in a wallet (sharedUi/.../ui/
 * replay/ReplayCard.kt ReplayCreditCard): 85.6 × 54mm, so 1.586:1, 20px
 * round, lifted on a 16dp shadow; the artwork it is about as a still mesh
 * (blur 34) deepened toward the foot (.18, .30, .55), where the cardholder's
 * name and the member-since date are embossed in a monospaced face, as a
 * card embosser's fixed-pitch dies would. What it is for sits top left and
 * the logo top right, so each corner carries one fact.
 */
export function ReplayCard({
  label,
  value,
  detail,
  art,
  holder,
  memberSince,
  onClick,
}: {
  label: string;
  value: string;
  detail: string | null;
  art: string | null | undefined;
  holder: string;
  /** `MM/YY`, or null when there is nothing to date it from. */
  memberSince: string | null;
  onClick?: () => void;
}) {
  const palette = useArtworkMesh(art);
  return (
    <button
      type="button"
      className="relative isolate flex aspect-[1.586] w-[300px] flex-none snap-start flex-col overflow-hidden rounded-[20px] px-[18px] py-4 text-start text-white shadow-[0_10px_28px_rgb(0_0_0/0.35),0_3px_8px_rgb(0_0_0/0.25)] transition-[scale] duration-150 ease-out enabled:active:scale-[.98] disabled:cursor-default"
      disabled={!onClick}
      onClick={onClick}
    >
      <div className="absolute inset-0 -z-1">
        <PaletteMesh palette={palette} blur={34} />
        <div className="absolute inset-0 bg-[linear-gradient(rgb(0_0_0/0.18),rgb(0_0_0/0.3)_55%,rgb(0_0_0/0.55))]" />
      </div>
      <span className="flex items-start gap-2.5">
        <span className="flex-1 type-[700_11px/13px] tracking-[1.4px] text-white/72 uppercase">Your listening experience</span>
        <LogoMark size={34} className="flex-none text-white" />
      </span>
      <span className="grow" />
      <span className="replay-emboss line-clamp-2 type-[800_27px/30px] tracking-[-0.25px]">{value}</span>
      <span className="type-[700_11px/13px] tracking-[1.8px] text-white/65 uppercase">{label}</span>
      <span className="grow-[0.85]" />
      <span className="flex items-end gap-2.5">
        <span className="flex min-w-0 flex-1 flex-col">
          <span className={`${emboss} text-[13px] leading-4`}>{holder}</span>
          {detail && <span className="truncate type-[600_11px/13px] tracking-[0.3px] text-white/60">{detail}</span>}
        </span>
        {memberSince && (
          <span className="flex flex-none flex-col items-end text-end">
            <span className="type-[600_7px/8px] tracking-[0.8px] text-white/55 uppercase">
              Member
              <br />
              since
            </span>
            <span className={`${emboss} text-[12px] leading-4`}>{memberSince}</span>
          </span>
        )}
      </span>
    </button>
  );
}
