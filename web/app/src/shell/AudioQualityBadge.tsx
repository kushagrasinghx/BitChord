import { useState } from 'react';
import { usePlayer } from '@/state/player';

/**
 * Apple Music authentic "Lossless" / "Hi-Res Lossless" quality badge and modal sheet.
 * Placed directly below the scrubber seekbar in FullScreenPlayer.
 * Conforms to Apple Music's exact minimal design:
 * - 18px height, rounded-[4px] subtle tag (never a pill/oval).
 * - Translucent monochrome glassmorphism with hairline border.
 * - Smooth, subtle hover transition (no bouncy scaling or loud glowing shadows).
 * - Exact Apple 3-bar waveform sound icon.
 * - Only visible when playing Lossless, Hi-Res Lossless, or Dolby Atmos streams.
 */
export function AudioQualityBadge({ className = '' }: { className?: string }) {
  const [modalOpen, setModalOpen] = useState(false);
  const nowPlaying = usePlayer((s) => s.nowPlaying);

  if (!nowPlaying) return null;

  const stream = nowPlaying.stream;
  const streamInfo = nowPlaying.streamInfo || '';
  const codec = (
    stream?.codec ||
    (streamInfo.includes('FLAC') ? 'FLAC' : streamInfo.includes('ALAC') ? 'ALAC' : streamInfo.includes('Opus') ? 'Opus' : 'AAC')
  ).toUpperCase();

  const sampleRateHz =
    stream?.sampleRateHz ||
    (streamInfo.includes('192') ? 192000 : streamInfo.includes('96') ? 96000 : streamInfo.includes('48') ? 48000 : 44100);
  const bitrateKbps = stream?.bitrateKbps || (streamInfo.includes('4608') ? 4608 : streamInfo.includes('1411') ? 1411 : 160);

  const isFlac =
    codec === 'FLAC' ||
    codec === 'ALAC' ||
    codec === 'WAV' ||
    streamInfo.includes('Lossless') ||
    streamInfo.includes('flac');
  const isHiRes =
    isFlac &&
    (sampleRateHz > 48000 || bitrateKbps > 1411 || streamInfo.includes('Hi-Res') || streamInfo.includes('24-bit'));
  const isDolbyAtmos = streamInfo.includes('Atmos') || (stream?.channels && stream.channels > 2);

  // Apple Music strictly shows the badge for enhanced audio (Lossless, Hi-Res Lossless, or Dolby Atmos)
  if (!isFlac && !isDolbyAtmos) return null;

  const label = isDolbyAtmos ? 'Dolby Atmos' : isHiRes ? 'Hi-Res Lossless' : 'Lossless';
  const sampleRateKhz = (sampleRateHz / 1000).toFixed(sampleRateHz % 1000 === 0 ? 0 : 1);
  const bitDepth = isHiRes ? 24 : 16;

  return (
    <>
      {/* Apple Music Native Scrubber Lossless Badge Container */}
      <div className="mt-1.5 mb-0.5 flex justify-center">
        <button
          type="button"
          onClick={() => setModalOpen(true)}
          className={`group inline-flex items-center gap-1.5 h-[18px] px-1.5 rounded-[4px] bg-white/[0.08] hover:bg-white/[0.15] active:bg-white/[0.22] border border-white/[0.1] hover:border-white/[0.18] backdrop-blur-md transition-all duration-200 ease-out cursor-pointer select-none text-white/80 hover:text-white ${className}`}
          title={`Audio Quality: ${label} (Click for details)`}
          aria-label={`Audio Quality: ${label}`}
        >
          {/* Apple Music 3-bar Waveform Icon */}
          <svg
            className="size-2.5 text-white/75 group-hover:text-white transition-colors duration-200"
            viewBox="0 0 16 16"
            fill="currentColor"
            aria-hidden="true"
          >
            <rect x="2" y="5.5" width="2.5" height="5" rx="1.25" />
            <rect x="6.75" y="2.5" width="2.5" height="11" rx="1.25" />
            <rect x="11.5" y="4.5" width="2.5" height="7" rx="1.25" />
          </svg>

          <span className="text-[10px] font-semibold tracking-[0.02em] leading-none">
            {label}
          </span>
        </button>
      </div>

      {/* Apple Music Audio Quality Modal Sheet */}
      {modalOpen && (
        <div
          className="fixed inset-0 z-50 flex items-center justify-center bg-black/60 backdrop-blur-md p-4 animate-fade-in"
          onClick={() => setModalOpen(false)}
        >
          <div
            className="relative w-full max-w-[320px] rounded-[20px] bg-[#232326]/90 border border-white/[0.12] p-5 shadow-[0_24px_64px_rgba(0,0,0,0.6)] text-center backdrop-blur-2xl"
            onClick={(e) => e.stopPropagation()}
          >
            {/* Close button */}
            <button
              type="button"
              onClick={() => setModalOpen(false)}
              className="absolute top-3.5 right-3.5 size-6 rounded-full bg-white/10 hover:bg-white/20 active:bg-white/30 grid place-items-center text-white/60 hover:text-white transition-colors cursor-pointer"
              aria-label="Close"
            >
              <svg width="10" height="10" viewBox="0 0 12 12" fill="none" stroke="currentColor" strokeWidth="2" strokeLinecap="round">
                <line x1="2" y1="2" x2="10" y2="10" />
                <line x1="10" y1="2" x2="2" y2="10" />
              </svg>
            </button>

            {/* Apple Music Hero Icon */}
            <div className="size-12 mx-auto rounded-[14px] bg-white/[0.08] border border-white/10 grid place-items-center text-white shadow-inner">
              <svg className="size-5 text-white/90" viewBox="0 0 16 16" fill="currentColor">
                <rect x="2" y="5.5" width="2.5" height="5" rx="1.25" />
                <rect x="6.75" y="2.5" width="2.5" height="11" rx="1.25" />
                <rect x="11.5" y="4.5" width="2.5" height="7" rx="1.25" />
              </svg>
            </div>

            <h3 className="mt-3 text-base font-semibold tracking-tight text-white">{label}</h3>
            <p className="mt-0.5 text-xs font-medium text-white/60">
              {isFlac ? `${codec} · ${bitDepth}-bit / ${sampleRateKhz} kHz` : label}
            </p>

            <p className="mt-3 text-[11.5px] leading-relaxed text-white/50 text-center">
              {isHiRes
                ? 'Hi-Res Lossless audio provides up to 24-bit, 192 kHz resolution. Bit-for-bit studio master accuracy with zero dynamic range loss.'
                : 'Lossless audio compression preserves all original audio data up to 24-bit, 48 kHz (Studio Master) with bit-perfect fidelity.'}
            </p>

            {/* Apple Music Technical Inset Group */}
            <div className="mt-4 rounded-xl bg-white/[0.04] border border-white/[0.08] p-2.5 text-[11px] divide-y divide-white/[0.06] text-start">
              <div className="flex justify-between py-1">
                <span className="text-white/40">Format</span>
                <span className="font-medium text-white">{codec}</span>
              </div>
              <div className="flex justify-between py-1">
                <span className="text-white/40">Resolution</span>
                <span className="font-medium text-white">{bitDepth}-bit Lossless</span>
              </div>
              <div className="flex justify-between py-1">
                <span className="text-white/40">Sample Rate</span>
                <span className="font-medium text-white">{sampleRateKhz} kHz</span>
              </div>
              <div className="flex justify-between py-1">
                <span className="text-white/40">Channels</span>
                <span className="font-medium text-white">Stereo</span>
              </div>
              <div className="flex justify-between py-1">
                <span className="text-white/40">Bitrate</span>
                <span className="font-medium text-white">~{bitrateKbps.toLocaleString()} kbps</span>
              </div>
              <div className="flex justify-between py-1">
                <span className="text-white/40">Source</span>
                <span className="font-medium text-white">Addon Stream</span>
              </div>
            </div>

            <button
              type="button"
              onClick={() => setModalOpen(false)}
              className="mt-4 w-full h-8.5 rounded-full bg-white/10 hover:bg-white/16 active:bg-white/22 text-xs font-medium text-white transition-colors cursor-pointer"
            >
              Done
            </button>
          </div>
        </div>
      )}
    </>
  );
}
