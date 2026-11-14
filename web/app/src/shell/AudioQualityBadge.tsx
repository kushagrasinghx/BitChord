import { useState } from 'react';
import { usePlayer } from '@/state/player';

export function AudioQualityBadge({ className = '', compact = false }: { className?: string; compact?: boolean }) {
  const [modalOpen, setModalOpen] = useState(false);
  const nowPlaying = usePlayer((s) => s.nowPlaying);

  if (!nowPlaying) return null;

  const stream = nowPlaying.stream;
  const streamInfo = nowPlaying.streamInfo || '';
  const codec = (stream?.codec || (streamInfo.includes('FLAC') ? 'FLAC' : streamInfo.includes('Opus') ? 'Opus' : 'AAC')).toUpperCase();
  const sampleRateHz = stream?.sampleRateHz || (streamInfo.includes('96') ? 96000 : streamInfo.includes('192') ? 192000 : streamInfo.includes('48') ? 48000 : 44100);
  const bitrateKbps = stream?.bitrateKbps || (streamInfo.includes('4608') ? 4608 : streamInfo.includes('1411') ? 1411 : 160);

  const isFlac = codec === 'FLAC' || streamInfo.includes('Lossless') || streamInfo.includes('flac');
  const isHiRes = isFlac && (sampleRateHz > 48000 || bitrateKbps > 1411 || streamInfo.includes('Hi-Res') || streamInfo.includes('24-bit'));
  const isDolbyAtmos = streamInfo.includes('Atmos') || (stream?.channels && stream.channels > 2);

  let label = 'High Quality';
  if (isDolbyAtmos) {
    label = 'Dolby Atmos';
  } else if (isHiRes) {
    label = 'Hi-Res Lossless';
  } else if (isFlac) {
    label = 'Lossless';
  } else if (codec === 'OPUS') {
    label = 'Opus Audio';
  } else if (codec === 'AAC') {
    label = 'AAC Audio';
  }

  // Format sample rate for display
  const sampleRateKhz = (sampleRateHz / 1000).toFixed(sampleRateHz % 1000 === 0 ? 0 : 1);
  const bitDepth = isHiRes ? 24 : isFlac ? 16 : 16;

  return (
    <>
      <button
        type="button"
        onClick={() => setModalOpen(true)}
        className={`group relative overflow-hidden inline-flex items-center gap-1.5 rounded-full border transition-all duration-300 ease-out cursor-pointer select-none backdrop-blur-xl saturate-150 ${
          compact ? 'px-2 py-0.5 text-[9.5px]' : 'px-3 py-1 text-[11px]'
        } ${
          isHiRes
            ? 'bg-amber-500/15 hover:bg-amber-500/25 border-amber-400/30 hover:border-amber-400/50 text-amber-200 shadow-[0_2px_12px_rgba(245,158,11,0.18),inset_0_1px_1px_rgba(255,255,255,0.25)] hover:shadow-[0_4px_22px_rgba(245,158,11,0.35)]'
            : isFlac
              ? 'bg-white/10 hover:bg-white/20 border-white/20 hover:border-white/35 text-primary shadow-[0_2px_12px_rgba(0,0,0,0.25),inset_0_1px_1px_rgba(255,255,255,0.3)] hover:shadow-[0_4px_22px_rgba(255,255,255,0.2)]'
              : 'bg-white/6 hover:bg-white/12 border-white/12 hover:border-white/25 text-secondary hover:text-primary shadow-[0_2px_8px_rgba(0,0,0,0.2),inset_0_1px_1px_rgba(255,255,255,0.15)] hover:shadow-[0_4px_16px_rgba(255,255,255,0.1)]'
        } hover:scale-105 active:scale-95 ${className}`}
        title={`Audio Quality: ${label} (Click for details)`}
        aria-label={`Audio Quality: ${label}`}
      >
        {/* Subtle glass specular highlight */}
        <span
          className="pointer-events-none absolute inset-x-0 top-0 h-1/2 bg-gradient-to-b from-white/25 to-transparent opacity-70 transition-opacity duration-300 group-hover:opacity-100"
          aria-hidden="true"
        />

        {/* Apple Music Lossless Waveform Icon */}
        <svg
          className={`relative z-1 shrink-0 transition-transform duration-300 group-hover:scale-110 ${
            isHiRes ? 'text-amber-400' : isFlac ? 'text-primary' : 'text-secondary'
          }`}
          width={compact ? '10' : '12'}
          height={compact ? '10' : '12'}
          viewBox="0 0 16 16"
          fill="none"
          stroke="currentColor"
          strokeWidth="1.8"
          strokeLinecap="round"
        >
          <line x1="2" y1="6" x2="2" y2="10" />
          <line x1="5" y1="3" x2="5" y2="13" />
          <line x1="8" y1="5" x2="8" y2="11" />
          <line x1="11" y1="2" x2="11" y2="14" />
          <line x1="14" y1="7" x2="14" y2="9" />
        </svg>

        <span className="relative z-1 font-semibold tracking-wider uppercase drop-shadow-[0_1px_2px_rgba(0,0,0,0.5)]">
          {label}
        </span>
      </button>

      {/* Apple Music Style Audio Quality Modal / Sheet */}
      {modalOpen && (
        <div
          className="fixed inset-0 z-50 flex items-center justify-center bg-black/65 backdrop-blur-2xl p-4 animate-fade-in"
          onClick={() => setModalOpen(false)}
        >
          <div
            className="relative w-full max-w-[360px] rounded-3xl bg-neutral-900/80 text-white p-6 shadow-[0_30px_90px_rgba(0,0,0,0.7),inset_0_1px_1px_rgba(255,255,255,0.3)] border border-white/20 backdrop-blur-3xl saturate-180 transition-all scale-100 animate-in fade-in zoom-in-95 duration-200"
            onClick={(e) => e.stopPropagation()}
          >
            {/* Top specular highlight for modal glass */}
            <span
              className="pointer-events-none absolute inset-x-0 top-0 h-16 rounded-t-3xl bg-gradient-to-b from-white/15 to-transparent"
              aria-hidden="true"
            />

            {/* Apple Music Lossless Graphic */}
            <div className="relative z-1 flex flex-col items-center text-center">
              <div
                className={`flex size-16 items-center justify-center rounded-2xl border shadow-[inset_0_1px_2px_rgba(255,255,255,0.35),0_8px_24px_rgba(0,0,0,0.3)] transition-transform duration-300 hover:scale-105 ${
                  isHiRes
                    ? 'border-amber-400/40 bg-gradient-to-b from-amber-400/25 to-yellow-600/15 text-amber-300'
                    : isFlac
                      ? 'border-white/25 bg-gradient-to-b from-white/20 to-white/5 text-white'
                      : 'border-white/15 bg-white/8 text-secondary'
                }`}
              >
                <svg width="30" height="30" viewBox="0 0 16 16" fill="none" stroke="currentColor" strokeWidth="1.8" strokeLinecap="round">
                  <line x1="2" y1="6" x2="2" y2="10" />
                  <line x1="5" y1="3" x2="5" y2="13" />
                  <line x1="8" y1="5" x2="8" y2="11" />
                  <line x1="11" y1="2" x2="11" y2="14" />
                  <line x1="14" y1="7" x2="14" y2="9" />
                </svg>
              </div>

              <h3 className="mt-4 text-xl font-bold tracking-tight text-white drop-shadow-sm">{label}</h3>
              <p className="mt-1 text-sm font-semibold text-white/80">
                {isFlac ? `${codec} · ${bitDepth}-bit / ${sampleRateKhz} kHz` : `${codec} · ${sampleRateKhz} kHz · ~${bitrateKbps} kbps`}
              </p>

              <p className="mt-3 text-xs leading-relaxed text-white/65">
                {isHiRes
                  ? 'Hi-Res Lossless audio provides up to 24-bit, 192 kHz resolution. Bit-for-bit studio master accuracy with zero dynamic range loss.'
                  : isFlac
                    ? 'Lossless audio compression preserves all original audio data up to 16-bit, 44.1 kHz (CD Quality) with pure studio fidelity.'
                    : 'Standard high-efficiency streaming audio optimized for fast buffering and reliable stereo playback.'}
              </p>
            </div>

            {/* Apple Style Technical Inset Details List */}
            <div className="relative z-1 mt-5 rounded-2xl bg-white/[0.04] border border-white/10 backdrop-blur-md p-3 text-xs divide-y divide-white/6 shadow-inner">
              <div className="flex justify-between py-1.5">
                <span className="text-white/50">Codec</span>
                <span className="font-medium text-white">{codec}</span>
              </div>
              <div className="flex justify-between py-1.5">
                <span className="text-white/50">Sample Rate</span>
                <span className="font-medium text-white">{sampleRateKhz} kHz ({sampleRateHz.toLocaleString()} Hz)</span>
              </div>
              <div className="flex justify-between py-1.5">
                <span className="text-white/50">Resolution</span>
                <span className="font-medium text-white">{isFlac ? `${bitDepth}-bit Lossless` : 'Compressed Audio'}</span>
              </div>
              <div className="flex justify-between py-1.5">
                <span className="text-white/50">Bitrate</span>
                <span className="font-medium text-white">~{bitrateKbps.toLocaleString()} kbps</span>
              </div>
              <div className="flex justify-between py-1.5">
                <span className="text-white/50">Channels</span>
                <span className="font-medium text-white">{stream?.channels === 1 ? 'Mono' : 'Stereo (2.0)'}</span>
              </div>
              <div className="flex justify-between py-1.5">
                <span className="text-white/50">Playback Mode</span>
                <span className="font-medium text-white">{isFlac ? 'Addon Stream Direct' : 'YouTube Opus Direct'}</span>
              </div>
            </div>

            {/* Done Button */}
            <button
              type="button"
              onClick={() => setModalOpen(false)}
              className="relative z-1 mt-5 w-full rounded-full bg-white/15 hover:bg-white/25 active:scale-98 border border-white/15 py-3 text-sm font-semibold text-white shadow-lg transition-all duration-200 cursor-pointer"
            >
              Done
            </button>
          </div>
        </div>
      )}
    </>
  );
}
