import './onboarding.css';
import { ChevronLeft } from 'lucide-react';
import { useEffect, useState, type ButtonHTMLAttributes, type CSSProperties, type ReactNode } from 'react';
import { FcGoogle } from 'react-icons/fc';
import { LogoMark } from '@/components/icons/LogoMark';
import { useYtAuth } from '@/extension/useYtAuth';
import type { Rgb } from '@/lib/meshPalette';
import { partySettings, savePartySettings } from '@/party/listenTogether';
import { rgbOf } from '@/replay/colors';
import { PaletteMesh, useReducedMotion } from '@/replay/PaletteMesh';
import { SIGN_IN_URL } from '@/shell/AccountButton';

/** Set once the welcome is done, either way; the phone's onboardingComplete. */
const FLAG = 'bitchord:onboarded';
/** Long enough for a full name; the party server keeps the first 80 (OnboardingScreen.kt NAME_LIMIT). */
const NAME_LIMIT = 40;
/** How long each palette is held, which is also how long the fade into the next takes (MESH_CYCLE_MILLIS). */
const CYCLE_MS = 3600;
const DRIFT_MS = 6000;
const PAGE_MS = 420;

const palettes = (...sets: number[][]): Rgb[][] => sets.map((set) => set.map(rgbOf));
/** BitChord's own colours, starting on Apple Music's red (MeshGradient.kt WelcomeMeshPalettes). */
const WELCOME = palettes(
  [0xfa2d48, 0x7b2ff7, 0xff7a45, 0x2e1a8a],
  [0xff4e8a, 0xff9a3c, 0xe0245e, 0x6a1b9a],
  [0x1e88e5, 0x00bfa5, 0x5e35b1, 0x0d47a1],
  [0x00c853, 0x00b8d4, 0x7c4dff, 0x1a237e],
  [0xd500f9, 0xff4081, 0x651fff, 0x4a148c],
  [0xffb300, 0xff5252, 0xf06292, 0x6d2e46],
);
/** The account page keeps leaning into YouTube's red while it moves (OnboardingScreen.kt ConnectPalettes). */
const CONNECT = palettes(
  [0xff1e3c, 0xc2185b, 0xff8f3d, 0x4a1c8c],
  [0xe53935, 0xff6e40, 0xad1457, 0x311b92],
  [0xff3d00, 0xd81b60, 0x8e24aa, 0xb71c1c],
  [0xf50057, 0xff9100, 0xc51162, 0x4527a0],
);

type Page = 'name' | 'connect';

function onboarded(): boolean {
  try {
    return localStorage.getItem(FLAG) !== null;
  } catch {
    // Storage blocked: asking again on every visit would be worse than not asking.
    return true;
  }
}

/**
 * The welcome, once (app/.../ui/onboarding/OnboardingScreen.kt): a name for
 * Listen Together, then the YouTube sign-in. Mounted by the shell; renders
 * nothing once `bitchord:onboarded` is set.
 */
export function Onboarding() {
  const [open, setOpen] = useState(() => !onboarded());
  return open ? <OnboardingScreen onDone={() => setOpen(false)} /> : null;
}

/**
 * The two pages over the whole window. Always dark, whatever the theme, as
 * the phone draws it: the mesh is colour, and white type is what sits on
 * colour. The second page is skipped for a browser already signed in to
 * Google, since asking to connect what is connected reads as forgetting.
 */
function OnboardingScreen({ onDone }: { onDone: () => void }) {
  const yt = useYtAuth();
  const signedIn = yt.state === 'ready' && yt.auth.browserSignedIn;
  const still = useReducedMotion();
  const [name, setName] = useState(() => partySettings().nickname.slice(0, NAME_LIMIT));
  const [view, setView] = useState<{ page: Page; from: Page | null }>({ page: 'name', from: null });
  const [closing, setClosing] = useState(false);
  const palette = useCyclingPalette(view.page === 'name' ? WELCOME : CONNECT, still);

  const finish = () => {
    try {
      localStorage.setItem(FLAG, '1');
    } catch {
      // Storage blocked: it closes for this visit.
    }
    if (still) onDone();
    else setClosing(true);
  };
  const go = (page: Page) => setView((v) => ({ page, from: v.page }));

  // The account landing is what ends the welcome, as on the phone.
  useEffect(() => {
    if (signedIn && view.page === 'connect' && !closing) finish();
  });
  // The page slid out is dropped once its slide is over.
  useEffect(() => {
    if (!view.from) return;
    const timer = setTimeout(() => setView((v) => ({ page: v.page, from: null })), PAGE_MS);
    return () => clearTimeout(timer);
  }, [view]);

  const next = () => {
    // A blank name is allowed: Listen Together then goes by the YouTube account's name (listenTogether.ts identity).
    savePartySettings({ nickname: name.trim() });
    if (signedIn) finish();
    else go('connect');
  };
  const signIn = () => {
    // AccountButton's own sign-in: Google's page in a new tab; the extension sees the session land.
    window.open(SIGN_IN_URL, '_blank', 'noreferrer');
    // Without the extension nothing here can see it land, so don't wait for it.
    if (yt.state === 'unavailable') finish();
  };

  const forward = view.page === 'connect';
  const render = (page: Page) =>
    page === 'name' ? (
      <NamePage name={name} onName={(value) => setName(value.slice(0, NAME_LIMIT))} onNext={next} />
    ) : (
      <ConnectPage onBack={() => go('name')} onSignIn={signIn} onSkip={finish} />
    );

  return (
    <div
      role="dialog"
      aria-modal="true"
      aria-label="Welcome to BitChord"
      className={`fixed inset-0 z-(--z-modal) overflow-hidden bg-black text-white ${closing ? 'ob-closing' : ''}`}
      onAnimationEnd={(e) => e.target === e.currentTarget && onDone()}
    >
      <PaletteMesh palette={palette} driftMs={DRIFT_MS} fadeMs={CYCLE_MS} />
      {/* Two full-width buttons and a paragraph want the foot a shade deeper than the mesh's own scrim. */}
      <div className="absolute inset-0 bg-[linear-gradient(rgb(0_0_0/0.05),transparent_55%,rgb(0_0_0/0.45))]" />
      {view.from && (
        <PageColumn key={view.from} className="ob-out" inert dir={forward ? 1 : -1}>
          {render(view.from)}
        </PageColumn>
      )}
      <PageColumn key={view.page} className={view.from ? 'ob-in' : ''} dir={forward ? 1 : -1}>
        {render(view.page)}
      </PageColumn>
    </div>
  );
}

/** Steps through [sets] every CYCLE_MS; reduced motion holds the first, as the phone's "Reduce animation" does. */
function useCyclingPalette(sets: Rgb[][], still: boolean): Rgb[] {
  const [step, setStep] = useState(0);
  useEffect(() => {
    if (still) return;
    const timer = setInterval(() => setStep((s) => s + 1), CYCLE_MS);
    return () => clearInterval(timer);
  }, [still]);
  return sets[(still ? 0 : step) % sets.length]!;
}

/**
 * Each page's column: the whole window to slide, the content capped at 520px
 * with 28px sides, the page's words up top and its controls at the foot, as
 * the phone lays it out. A desktop window (1000px and up) has the width the
 * phone hasn't: the words take the left half and the controls sit beside
 * them, centred in the window, rather than a phone's column with a tall gap.
 */
function PageColumn({ className, inert, dir, children }: { className: string; inert?: boolean; dir: number; children: ReactNode }) {
  return (
    <div className={`absolute inset-0 flex justify-center ${className}`} inert={inert} style={{ '--ob-dir': dir } as CSSProperties}>
      <div className="flex h-full w-full max-w-[520px] flex-col px-7 pt-4 pb-6 lg:max-w-[1080px] lg:flex-row lg:items-center lg:gap-20 lg:px-16 lg:py-12">
        {children}
      </div>
    </div>
  );
}

/** A page's two parts: what it says, a spacer the phone pushes the controls down with, and the controls. */
function PageParts({ words, controls }: { words: ReactNode; controls: ReactNode }) {
  return (
    <>
      <div className="flex flex-none flex-col lg:flex-1">{words}</div>
      <div className="flex-1 lg:hidden" />
      <div className="flex flex-none flex-col lg:w-[400px]">{controls}</div>
    </>
  );
}

/** Replay's heading, scaled up (and again for a desktop window): the one line each page exists to say. */
function Title({ children }: { children: ReactNode }) {
  return <h1 className="type-[800_40px/44px] tracking-[-1.2px] text-balance text-white lg:type-[800_60px/62px] lg:tracking-[-2.2px]">{children}</h1>;
}

/**
 * A full-width 56px capsule, 17px bold (PillButton): white on the mesh as
 * Apple Music's own welcome does it, or the same shape in frosted white, so a
 * pair reads as a choice. It sinks to 97% while pressed and dims to 40% when
 * it can't be used.
 */
function Pill({ primary, className = '', ...button }: { primary: boolean } & ButtonHTMLAttributes<HTMLButtonElement>) {
  return (
    <button
      type="button"
      className={`flex h-14 w-full flex-none items-center justify-center gap-2.5 rounded-pill type-[700_17px/22px] transition-[scale,opacity] duration-[150ms,180ms] ease-out enabled:active:scale-[.97] disabled:opacity-40 ${primary ? 'bg-white text-black' : 'bg-white/16 text-white'} ${className}`}
      {...button}
    />
  );
}

function NamePage({ name, onName, onNext }: { name: string; onName: (name: string) => void; onNext: () => void }) {
  return (
    <PageParts
      words={
        <>
          <LogoMark size={46} className="mt-14 flex-none text-white lg:mt-0" />
          <div className="mt-6">
            <Title>Millions of songs, stream on BitChord</Title>
          </div>
        </>
      }
      controls={
        <>
          <label htmlFor="onboarding-name" className="ps-1 type-[600_17px/22px] text-white/78">
            What should we call you?
          </label>
          {/* The name in the heading's own face and weight, so it reads as part of the page, not a form on top of it. */}
          <input
            id="onboarding-name"
            className="mt-3 h-16 w-full flex-none rounded-pill border-0 bg-white/14 px-6 type-[800_26px/30px] tracking-[-0.6px] text-white caret-white outline-none placeholder:text-white/38 focus-visible:bg-white/20"
            placeholder="Your name"
            value={name}
            maxLength={NAME_LIMIT}
            autoFocus
            autoComplete="nickname"
            autoCapitalize="words"
            spellCheck={false}
            onChange={(e) => onName(e.currentTarget.value)}
            onKeyDown={(e) => {
              if (e.key !== 'Enter') return;
              // Or the same Enter goes on to press whatever the next page focuses.
              e.preventDefault();
              onNext();
            }}
          />
          <p className="mt-2.5 px-1 type-callout text-white/55">
            Friends see this when you Listen Together; leave it blank to go by your YouTube name. You can change it later in Settings.
          </p>
          <Pill primary className="mt-7" onClick={onNext}>
            Next
          </Pill>
        </>
      }
    />
  );
}

function ConnectPage({ onBack, onSignIn, onSkip }: { onBack: () => void; onSignIn: () => void; onSkip: () => void }) {
  return (
    <PageParts
      words={
        <>
          <button
            type="button"
            aria-label="Back"
            className="grid size-10 flex-none place-items-center self-start rounded-full bg-white/16 text-white transition-colors hover:bg-white/24 focus-visible:bg-white/28"
            onClick={onBack}
          >
            <ChevronLeft size={20} strokeWidth={2.6} />
          </button>
          <div className="mt-5">
            <Title>Connect your YouTube account</Title>
          </div>
          <p className="mt-3.5 type-[400_17px/23px] text-white/72 lg:max-w-[40ch]">
            Bring your library, playlists and mixes with you, and start a Listen Together party with friends.
          </p>
        </>
      }
      controls={
        <>
          <Pill primary onClick={onSignIn}>
            <FcGoogle size={20} aria-hidden />
            Sign in with Google
          </Pill>
          <Pill primary={false} className="mt-3" onClick={onSkip}>
            Skip sign in
          </Pill>
        </>
      }
    />
  );
}
