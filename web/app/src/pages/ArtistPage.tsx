import { AudioLines, Shuffle, Star, User } from 'lucide-react';
import { useMemo, useState } from 'react';
import { useParams } from 'react-router';
import { PrefetchLink } from '@/components/PrefetchLink';
import { Artwork } from '@/components/music/Artwork';
import { AboutSection, pressable, veil } from '@/components/music/DetailHeader';
import { albumHref, browseHref } from '@/components/music/links';
import { playList, shuffleList } from '@/components/music/play';
import { Shelves, ShelfScroller } from '@/components/music/ShelfView';
import { CompactSongRow } from '@/components/music/SongRows';
import { LoadError, NeedsExtension } from '@/components/music/States';
import { requestAppleAccess, useAppleArtistArt, useUngrantedAppleOrigins, type AppleArtistArt } from '@/data/apple/artistArt';
import { recentLabel, topRelease } from '@/data/music/detail';
import { useArtist, useSetSubscribed, useSignedIn, useYouTubeReady } from '@/data/music/queries';
import type { MusicItem } from '@/data/music/types';
import type { ArtworkColor } from '@/lib/artworkColor';
import { usePageTint } from '@/lib/usePageTint';
import { ArtistSkeleton } from '@/skeletons/PageSkeletons';

function luminance(color: string): number {
  const [r, g, b] = [1, 3, 5].map((i) => parseInt(color.slice(i, i + 2), 16) / 255);
  return 0.2126 * r! + 0.7152 * g! + 0.0722 * b!;
}

/**
 * The name, or Apple's title logo in its place (AppleArtistArt.kt). Until the
 * logo has drawn, the name stands in, so the header is never left empty; a
 * logo that fails to load leaves the name.
 */
function ArtistName({ name, apple }: { name: string; apple: AppleArtistArt | null }) {
  const logo = apple?.logoUrl ?? null;
  const [shown, setShown] = useState<string | null>(null);
  const logoShown = !!logo && shown === logo;
  return (
    // displayLarge, 34/800, two lines at most; no shadow: the wash under it is what keeps it legible.
    <h1 className="line-clamp-2 type-header-emphasized text-primary data-logo:w-full lg:max-w-[min(70%,820px)]" data-logo={logoShown || undefined}>
      {!logoShown && name}
      {logo && (
        <img
          key={logo}
          // Apple's title logo in place of the name: at most 82% of the page's width and 30% of the
          // photo's height (ARTIST_LOGO_WIDTH, ARTIST_LOGO_MAX_HEIGHT; the photo is the column's width
          // over 0.95, and never taller than 60vh), sitting 13px higher than the name, as its 20px foot does.
          // On the desktop's banner the logo sits at the start with the actions, and keeps to a third of the banner's height.
          className="mx-auto mt-0 mb-[13px] block h-auto max-h-[min(calc((100vw-var(--web-navigation-width))/0.95*0.3),18vh)] w-auto max-w-[min(82%,640px)] lg:ms-0 lg:max-h-[min(14vh,120px)] lg:max-w-[min(100%,520px)]"
          src={logo}
          alt={logoShown ? name : ''}
          hidden={!logoShown}
          // Its proportions are known before it decodes; the CSS caps its size.
          style={{ aspectRatio: String(apple!.logoAspect) }}
          decoding="async"
          onLoad={() => setShown(logo)}
        />
      )}
    </h1>
  );
}

/**
 * The artist page's Play (DetailScreen.kt PlayCircle): a 70px disc with the
 * triangle punched out of it, so the page shows through, as Apple's does. The
 * triangle is the play glyph's, 56% of the disc (ARTIST_PLAY_ICON_SCALE),
 * drawn with the same round-joined 2-unit pen.
 */
function PlayCircle({ fill, label, disabled, onClick }: { fill: string; label: string; disabled: boolean; onClick: () => void }) {
  // The disc in the glyph's 24-unit space: 24 / 0.56 across, centred on it.
  const half = 12 / 0.56;
  return (
    <button
      className="size-[70px] flex-none rounded-full transition-[scale] duration-150 ease-out enabled:active:scale-[.94] disabled:opacity-40"
      style={{ color: fill }}
      aria-label={label}
      disabled={disabled}
      onClick={onClick}
    >
      <svg className="size-full" viewBox={`${12 - half} ${12 - half} ${half * 2} ${half * 2}`} aria-hidden="true">
        <mask id="artist-play-cutout">
          <rect x={12 - half} y={12 - half} width={half * 2} height={half * 2} fill="#fff" />
          <path d="M6.8 4.8 19.2 12 6.8 19.2Z" fill="#000" stroke="#000" strokeWidth={2} strokeLinejoin="round" />
        </mask>
        <circle cx={12} cy={12} r={half} fill="currentColor" mask="url(#artist-play-cutout)" />
      </svg>
    </button>
  );
}

/**
 * The newest release, pulled out of the shelves as a card of its own
 * (DetailScreen.kt TopReleaseCard): a 24px corner, a 96px sleeve 12px in with
 * its corner concentric (24 less 12), the text 12px after it; the kind and
 * year, and Recent Album, at 55% of the title's colour. A light veil with a
 * light hairline, as Apple's containers read slightly white against the page.
 */
function TopReleaseCard({ item }: { item: MusicItem }) {
  const href = albumHref(item.id);
  if (!href) return null;
  return (
    <PrefetchLink
      to={href}
      className={`mx-(--body-gutter) mb-[22px] flex max-w-[560px] items-center gap-3 rounded-[24px] p-3 transition-colors hover:bg-[color-mix(in_srgb,var(--systemPrimary)_12%,transparent)] ${veil}`}
    >
      <div className="w-24 flex-none rounded-[12px] ring-1 ring-[#ffffff37]">
        <Artwork src={item.thumbnail} displayWidth={96} radius={12} />
      </div>
      <div className="flex min-w-0 flex-1 flex-col">
        <p className="truncate type-callout text-primary/55">{item.subtitle}</p>
        <p className="line-clamp-2 type-[700_20px/22px] tracking-[-0.2px] text-primary">{item.title}</p>
        <p className="truncate type-callout text-primary/55">{recentLabel(item)}</p>
      </div>
    </PrefetchLink>
  );
}

/** The artist's count as the phone's About shows it (StatChip): a glass pill, a 15px glyph, labelMedium. */
function StatChip({ text }: { text: string }) {
  const Icon = /subscriber/i.test(text) ? User : AudioLines;
  return (
    <p className="mt-3 inline-flex items-center gap-1.5 rounded-pill bg-[color-mix(in_srgb,var(--surface)_60%,transparent)] px-3 py-1.5 type-label-medium text-secondary [backdrop-filter:blur(20px)]">
      <Icon size={15} aria-hidden="true" />
      {text}
    </p>
  );
}

/** At most twenty, paged sideways four to a column (MAX_ARTIST_SONGS, SONGS_PER_COLUMN). */
const MAX_TOP_SONGS = 20;

/**
 * An artist (DetailScreen.kt ArtistHeader and PageBackground): the photo a
 * shade taller than wide, its foot blurred and washed into the page, the name
 * across it; Shuffle · Play · Subscribe; the newest release; Top songs; the
 * shelves; then About with the artist's count. Where Apple Music has the
 * artist, the page takes Apple's header as the phone does: its photograph, its
 * title logo over it, its colour behind the whole page, and on the Play disc.
 */
export default function ArtistPage() {
  const id = decodeURIComponent(useParams().id ?? '');
  const { missing } = useYouTubeReady();
  const signedIn = useSignedIn();
  const artist = useArtist(id);
  const subscribe = useSetSubscribed();
  const apple = useAppleArtistArt(artist.data?.name);
  const appleUngranted = useUngrantedAppleOrigins();
  const tint = useMemo<ArtworkColor | null>(
    () => (apple ? { background: apple.background, dark: luminance(apple.background) < 0.6 } : null),
    [apple],
  );
  usePageTint(tint);
  const release = useMemo(() => topRelease(artist.data?.shelves ?? []), [artist.data?.shelves]);

  if (missing) return <NeedsExtension />;
  if (artist.isError) return <LoadError error={artist.error} onRetry={() => void artist.refetch()} />;
  if (!artist.data) return <ArtistSkeleton />;

  const data = artist.data;
  const songs = data.topSongs.slice(0, MAX_TOP_SONGS);
  const context = { source: { title: `${data.name}: Top Songs`, type: 'artist' as const, id: data.id } };
  const wash = 'var(--page-tint,var(--pageBG))';
  const subscription = data.subscription;
  const subscribed = subscription?.subscribed ?? false;
  const canSubscribe = signedIn && !!subscription;
  return (
    <>
      <header>
        {/* ARTIST_PHOTO_RATIO .95, and never more than 60% of the window's height. From 1000px a wide banner
            instead, the photo cropped landscape and no taller than 42% of the window, as a desktop player heads an artist. */}
        <div className="relative aspect-[0.95] max-h-[60vh] w-full overflow-hidden lg:aspect-[1165/604] lg:max-h-[42vh] lg:min-h-[320px]">
          <div className="absolute inset-0 [&>div]:h-full">
            <Artwork src={data.heroImage} displayWidth={1200} aspect={1165 / 604} eager />
          </div>
          {/* Apple's photograph fades in over YouTube's once it arrives; on the banner its upper part, where the face is. */}
          {apple && (
            <img
              key={apple.heroUrl}
              className="absolute inset-0 size-full object-cover opacity-0 transition-opacity duration-400 ease-in data-loaded:opacity-100 lg:object-[50%_28%]"
              src={apple.heroUrl}
              alt=""
              decoding="async"
              onLoad={(e) => e.currentTarget.setAttribute('data-loaded', '')}
            />
          )}
          {/* The foot loses its detail before its colour (SOFT_FOOT_BLUR 48, SOFT_FOOT_MASK .55 to .85): a merge rather than a fade to a flat tint. */}
          <div
            className="pointer-events-none absolute inset-0 backdrop-blur-[48px] [mask-image:linear-gradient(180deg,transparent_55%,#000_85%)]"
            aria-hidden="true"
          />
          {/* Then into the page's colour, eased over five stops so no stop shows as a line (PageBackground). */}
          <div
            className="pointer-events-none absolute inset-0"
            style={{
              background: `linear-gradient(180deg, transparent 45%, color-mix(in srgb, ${wash} 30%, transparent) 65%, color-mix(in srgb, ${wash} 72%, transparent) 82%, color-mix(in srgb, ${wash} 95%, transparent) 94%, ${wash} 100%)`,
            }}
            aria-hidden="true"
          />
          {/* On the banner the name and actions stand at the start, over more of the photo than on a phone:
              the page's colour leans in from that side too, so they read on a bright picture. */}
          <div
            className="pointer-events-none absolute inset-0 hidden bg-[linear-gradient(90deg,color-mix(in_srgb,var(--page-tint,var(--pageBG))_62%,transparent),transparent_58%)] lg:block"
            aria-hidden="true"
          />
          {/* The name hangs 20px up into the photo's foot (HEADER_DROP less ARTIST_HEADER_LIFT), 7px over the actions.
              On the banner it sits at the start, over the actions, both on the photo's washed foot. */}
          <div className="absolute inset-x-0 bottom-[27px] flex flex-col items-center px-(--body-gutter) text-center lg:bottom-[98px] lg:items-start lg:text-start">
            <ArtistName name={data.name} apple={apple} />
          </div>
        </div>
        {/* Shuffle 52 · Play 70 · Subscribe 52, 24 apart (ARTIST_SIDE_BUTTON, ARTIST_PLAY_BUTTON, ARTIST_ACTION_GAP), 22 below.
            On the banner the row starts on the gutter, 20px above the photo's foot. */}
        <div className="relative -mt-5 mb-[22px] flex flex-col items-center gap-3 px-(--body-gutter) lg:-mt-[90px] lg:mb-8 lg:items-start">
          <div className="flex items-center gap-6 lg:gap-4">
            <button
              className={`grid size-[52px] flex-none place-items-center rounded-full ${veil} ${pressable} [&_svg]:size-[23px]`}
              aria-label="Shuffle"
              title="Shuffle"
              disabled={!songs.length}
              onClick={() => shuffleList(songs, context)}
            >
              <Shuffle strokeWidth={2.2} />
            </button>
            <PlayCircle
              // Apple's fill for the disc where it has the artist; white otherwise.
              fill={apple?.keyColor ?? '#ffffff'}
              label={`Play ${data.name}`}
              disabled={!songs.length}
              onClick={() => playList(songs, 0, context)}
            />
            <button
              // The phone's subscribe star (DetailScreen's Star / StarBorder), greyed out where it can't be
              // used rather than leaving Play off-centre: a subscription is the account's (MainActivity).
              className={`grid size-[52px] flex-none place-items-center rounded-full ${veil} ${pressable} [&_svg]:size-[23px]`}
              aria-label={subscribed ? 'Unsubscribe' : 'Subscribe'}
              aria-pressed={canSubscribe ? subscribed : undefined}
              title={!signedIn ? 'Sign in to subscribe' : subscribed ? 'Unsubscribe' : 'Subscribe'}
              disabled={!canSubscribe}
              onClick={() =>
                subscription &&
                subscribe.mutate({ artistId: id, channelId: subscription.channelId, subscribed: !subscribed })
              }
            >
              <Star strokeWidth={2.2} fill={subscribed ? 'currentColor' : 'none'} />
            </button>
          </div>
          {!apple && appleUngranted.length > 0 && (
            // The lyrics panel's permission notice, as a pill under the actions.
            <div className="flex items-center gap-2.5 rounded-[999px] bg-[#0000004d] py-1.5 pr-2 pl-3.5 type-callout text-[#ffffffcc] [backdrop-filter:blur(20px)_saturate(1.6)]">
              <span>Apple Music artist art needs the extension’s permission.</span>
              <button
                className="flex-none rounded-[999px] bg-[#ffffff26] px-2.5 py-1 type-callout-emphasized text-white hover:bg-[#ffffff40]"
                // Stays until the extension confirms the grant (checked when this tab comes back).
                onClick={requestAppleAccess}
              >
                Allow
              </button>
            </div>
          )}
        </div>
      </header>

      {release && <TopReleaseCard item={release} />}

      {songs.length > 0 && (
        <section aria-label="Top songs">
          {/* SectionHeading: headlineMedium, Show all in the accent. */}
          <div className="mx-(--body-gutter) mt-2.5 mb-2 flex items-center justify-between gap-3">
            <h2 className="type-title-1-emphasized">Top songs</h2>
            {data.topSongsSeeAll && (
              <PrefetchLink to={browseHref(data.topSongsSeeAll)} className="type-callout-medium text-primary hover:underline">
                Show all
              </PrefetchLink>
            )}
          </div>
          <ShelfScroller key={songs.length} kind="tracks">
            {songs.map((track, i) => (
              <li key={track.videoId}>
                <CompactSongRow track={track} onPlayRow={() => playList(songs, i, context)} />
              </li>
            ))}
          </ShelfScroller>
        </section>
      )}

      <Shelves shelves={data.shelves} />

      {/* On an artist page About closes the page, after the shelves, with the artist's count under it. */}
      <div className="mt-7">
        <AboutSection title="About the artist" text={data.description}>
          {data.audience && <StatChip text={data.audience} />}
        </AboutSection>
      </div>
    </>
  );
}
