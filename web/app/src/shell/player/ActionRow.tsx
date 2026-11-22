import { Infinity as InfinityIcon } from 'lucide-react';
import { useId, type ComponentType, type ReactNode } from 'react';
import { useNavigate } from 'react-router';
import { LyricsIcon, ShuffleIcon, RepeatIcon, UpNextIcon } from '@/components/icons/Transport';
import { config } from '@/config';
import { party, useParty } from '@/party/listenTogether';
import { usePlayer } from '@/state/player';
import { PILL_SPRING } from './motion';

export type Pane = 'main' | 'lyrics' | 'queue';

/** PlayerControls.kt BOTTOM_ACTION_SIZE, BOTTOM_ICON_SIZE: 26px glyphs in 44px targets. */
const ACTION = 44;
const ICON = 26;
/** PILL_SEGMENT_WIDTH, and a capsule of n segments with its 1px dividers (pillWidth). */
const SEGMENT = 64;
const pillWidth = (segments: number) => SEGMENT * segments + (segments - 1);

// Material's rounded Person: the party half of the phone's capsule, drawn while in a party.
const PERSON =
  'M12 12c2.21 0 4-1.79 4-4s-1.79-4-4-4-4 1.79-4 4 1.79 4 4 4zm0 2c-2.67 0-8 1.34-8 4v1c0 .55.45 1 1 1h14c.55 0 1-.45 1-1v-1c0-2.66-5.33-4-8-4z';
// Material's rounded Speaker, so "This computer" under it reads as where the music plays.
const SPEAKER =
  'M17 2H7c-1.1 0-2 .9-2 2v16c0 1.1.9 1.99 2 1.99L17 22c1.1 0 2-.9 2-2V4c0-1.1-.9-2-2-2zm-5 2c1.1 0 2 .9 2 2s-.9 2-2 2c-1.11 0-2-.9-2-2s.89-2 2-2zm0 16c-2.76 0-5-2.24-5-5s2.24-5 5-5 5 2.24 5 5-2.24 5-5 5zm0-8c-1.66 0-3 1.34-3 3s1.34 3 3 3 3-1.34 3-3-1.34-3-3-3z';
function Glyph({ d, size }: { d: string; size: number }) {
  return (
    <svg width={size} height={size} viewBox="0 0 24 24" fill="currentColor" aria-hidden="true">
      <path d={d} />
    </svg>
  );
}

type Glyph = ComponentType<{ size?: number }>;

/**
 * The lyrics and queue glyphs (PlayerControls.kt BottomGlyph): white at 75%,
 * and lit Apple's way, a 35px tile with 7px corners at 62% white with the
 * glyph cut out of it, so whatever is behind shows through the glyph. The
 * cut-out is an SVG mask holding the glyph in black.
 */
function BottomGlyph({ icon: Icon, label, lit, onClick, className = '' }: { icon: Glyph; label: string; lit: boolean; onClick: () => void; className?: string }) {
  const mask = useId();
  return (
    <button
      className={`relative grid flex-none place-items-center text-white/75 transition-[translate] duration-220 ease-[cubic-bezier(0.4,0,0.2,1)] motion-reduce:transition-none ${className}`}
      style={{ width: ACTION, height: ACTION }}
      aria-label={label}
      aria-pressed={lit}
      onClick={onClick}
    >
      {lit ? (
        <svg width={35} height={35} viewBox="0 0 35 35" aria-hidden="true">
          <mask id={mask}>
            <rect width={35} height={35} rx={7} fill="#fff" />
            <svg x={(35 - ICON) / 2} y={(35 - ICON) / 2} width={ICON} height={ICON} color="#000" overflow="visible">
              <Icon size={ICON} />
            </svg>
          </mask>
          <rect width={35} height={35} rx={7} fill="#fff" fillOpacity={0.62} mask={`url(#${mask})`} />
        </svg>
      ) : (
        <Icon size={ICON} />
      )}
    </button>
  );
}

/** One control in the capsule (PillSegment): 64px, its glyph at 75% white, lit with a 14% white fill. */
function Segment({ label, lit, onClick, children }: { label: string; lit?: boolean; onClick: () => void; children: ReactNode }) {
  return (
    <button
      className="grid h-full flex-none place-items-center text-white/75 transition-colors duration-200 aria-pressed:bg-white/14 aria-pressed:text-white"
      style={{ width: SEGMENT }}
      aria-label={label}
      aria-pressed={lit}
      onClick={onClick}
    >
      {children}
    </button>
  );
}

const Divider = () => <span className="h-full w-px flex-none bg-white/20" aria-hidden="true" />;

/** Shuffle, repeat and AutoPlay: the capsule while the queue is up (PlayerActionRow's showQueueModes). */
function QueueModes({ inert }: { inert: boolean }) {
  const shuffle = usePlayer((s) => s.shuffle);
  const repeat = usePlayer((s) => s.repeat);
  const localAutoplay = usePlayer((s) => s.autoplay);
  // In a party AutoPlay is the party's setting, as Up Next has it.
  const inParty = useParty((s) => !!s.code);
  const partyAutoplay = useParty((s) => s.playback.autoplayEnabled);
  const autoplay = inParty ? partyAutoplay : localAutoplay;
  const { toggleShuffle, cycleRepeat, setAutoplay } = usePlayer.getState();
  return (
    <div className="flex h-full" inert={inert}>
      <Segment label="Shuffle" lit={shuffle} onClick={toggleShuffle}>
        <ShuffleIcon size={24} />
      </Segment>
      <Divider />
      <Segment
        label={repeat === 'one' ? 'Repeat one' : repeat === 'all' ? 'Repeat all' : 'Repeat'}
        lit={repeat !== 'off'}
        onClick={cycleRepeat}
      >
        {/* Repeat one is the figure alone, 17px bold, as the phone draws it. */}
        {repeat === 'one' ? <span className="type-[700_17px/20px]">1</span> : <RepeatIcon size={24} />}
      </Segment>
      <Divider />
      <Segment label="AutoPlay" lit={autoplay} onClick={() => (inParty ? party.setAutoplay(!autoplay) : setAutoplay(!autoplay))}>
        <InfinityIcon size={22} strokeWidth={2.4} />
      </Segment>
    </div>
  );
}

/**
 * Lyrics, the capsule, and the queue, with the output caption under them
 * (PlayerControls.kt PlayerActionRow and OutputCaption).
 *
 * The capsule (44px, white at 12%) swaps by what is open: normally where the
 * music goes, and the three playback modes while the queue is up, since then
 * they are what you reach for. The old contents fade out over 140ms and the
 * new in over 180 after a 140 wait while the capsule's width springs between
 * the two. The web has no output picker, so the normal capsule is Listen
 * Together alone, drawn as the phone draws a lone output glyph: a plain glyph,
 * the lyrics and queue glyphs drawn 16px closer in around it and parting as
 * the queue opens (PlayerActionRow's compactness, 220ms).
 */
export function ActionRow({ pane, onPane }: { pane: Pane; onPane: (pane: Pane) => void }) {
  const navigate = useNavigate();
  const queueOpen = pane === 'queue';
  const inParty = useParty((s) => !!s.code);
  const members = useParty((s) => s.members);
  // Listen Together needs a party server; without one there is nothing to offer.
  const partyOffered = !!config.partyServerUrl || inParty;
  const compact = !queueOpen;
  const width = queueOpen ? pillWidth(3) : partyOffered ? ACTION : 0;
  // The three-up capsule's spacing, and the lone glyph's closer one: (W − 2·44 − pill) / 4 each side.
  const closeIn = (pillWidth(3) - pillWidth(2)) / 4;
  const host = members.find((m) => m.isHost)?.displayName?.trim().split(/\s+/)[0];
  const openParty = () => navigate('/settings/listen-together');

  return (
    <div className="flex w-full flex-col items-center gap-[18px]">
      <div
        className="flex w-full items-center justify-between"
        style={{ paddingInline: `max(0px, calc((100% - ${ACTION * 2 + pillWidth(3)}px) / 4))`, '--close-in': `${closeIn}px` } as React.CSSProperties}
      >
        <BottomGlyph
          icon={LyricsIcon}
          label={pane === 'lyrics' ? 'Close lyrics' : 'Lyrics'}
          lit={pane === 'lyrics'}
          onClick={() => onPane(pane === 'lyrics' ? 'main' : 'lyrics')}
          className={compact ? 'translate-x-(--close-in)' : ''}
        />
        <div
          className={`relative h-11 overflow-hidden rounded-pill transition-[width,background-color] motion-reduce:transition-none ${PILL_SPRING} ${queueOpen ? 'bg-white/12' : ''}`}
          style={{ width }}
        >
          <div
            className={`absolute inset-0 flex justify-center transition-opacity motion-reduce:transition-none ${queueOpen ? 'opacity-0 duration-140' : 'opacity-100 delay-140 duration-180'}`}
          >
            {partyOffered && (
              <button
                className="grid size-11 flex-none place-items-center text-white/75 aria-pressed:text-white"
                aria-label={inParty ? `Listen Together, ${members.length} listening` : 'Listen Together'}
                aria-pressed={inParty}
                inert={queueOpen}
                onClick={openParty}
              >
                <Glyph d={inParty ? PERSON : SPEAKER} size={22} />
              </button>
            )}
          </div>
          <div
            className={`absolute inset-y-0 left-1/2 flex -translate-x-1/2 transition-opacity motion-reduce:transition-none ${queueOpen ? 'opacity-100 delay-140 duration-180' : 'opacity-0 duration-140'}`}
          >
            <QueueModes inert={!queueOpen} />
          </div>
        </div>
        <BottomGlyph
          icon={UpNextIcon}
          label={queueOpen ? 'Close queue' : 'Queue'}
          lit={queueOpen}
          onClick={() => onPane(queueOpen ? 'main' : 'queue')}
          className={compact ? '-translate-x-(--close-in)' : ''}
        />
      </div>
      {/* OutputCaption: 11px at 55% white, the party's name whenever there is one. */}
      {inParty ? (
        <button className="max-w-[65%] truncate type-[600_11px/13px] text-white/55" onClick={openParty}>
          {host ? `${host}’s Jam` : 'Listening together'}
        </button>
      ) : (
        <span className="max-w-[65%] truncate type-[600_11px/13px] text-white/55">This computer</span>
      )}
    </div>
  );
}
