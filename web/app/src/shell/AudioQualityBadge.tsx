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
        className={`group inline-flex items-center gap-1.5 rounded-[5px] border border-white/10 bg-white/8 hover:bg-white/15 active:bg-white/20 transition-all duration-150 backdrop-blur-md cursor-pointer select-none text-primary ${
          compact ? 'px-1.5 py-0.5 text-[9.5px]' : 'px-2 py-0.5 text-[10.5px]'
        } ${className}`}
        title={`Audio Quality: ${label} (Click for details)`}
        aria-label={`Audio Quality: ${label}`}
      >
        {/* Apple Music Lossless Waveform Icon */}
        <svg
          className={`shrink-0 ${isHiRes ? 'text-amber-400' : isFlac ? 'text-primary' : 'text-secondary'}`}
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

        <span className="font-semibold tracking-wide uppercase">{label}</span>
      </button>

      {/* Apple Music Style Audio Quality Modal / Sheet */}
      {modalOpen && (
        <div
          className="fixed inset-0 z-50 flex items-center justify-center bg-black/60 backdrop-blur-xl p-4 animate-fade-in"
          onClick={() => setModalOpen(false)}
        >
          <div
            className="relative w-full max-w-[360px] rounded-3xl bg-[#1c1c1e]/95 text-white p-6 shadow-2xl border border-white/12 backdrop-blur-2xl transition-all scale-100"
            onClick={(e) => e.stopPropagation()}
          >
            {/* Apple Music Lossless Graphic */}
            <div className="flex flex-col items-center text-center">
              <div
                className={`flex size-14 items-center justify-center rounded-2xl border shadow-inner ${
                  isHiRes
                    ? 'border-amber-400/30 bg-gradient-to-b from-amber-400/20 to-yellow-600/10 text-amber-300'
                    : isFlac
                      ? 'border-white/20 bg-white/10 text-white'
                      : 'border-white/10 bg-white/5 text-secondary'
                }`}
              >
                <svg width="28" height="28" viewBox="0 0 16 16" fill="none" stroke="currentColor" strokeWidth="1.8" strokeLinecap="round">
                  <line x1="2" y1="6" x2="2" y2="10" />
                  <line x1="5" y1="3" x2="5" y2="13" />
                  <line x1="8" y1="5" x2="8" y2="11" />
                  <line x1="11" y1="2" x2="11" y2="14" />
                  <line x1="14" y1="7" x2="14" y2="9" />
                </svg>
              </div>

              <h3 className="mt-4 text-xl font-bold tracking-tight text-white">{label}</h3>
              <p className="mt-1 text-sm font-semibold text-white/80">
                {isFlac ? `${codec} · ${bitDepth}-bit / ${sampleRateKhz} kHz` : `${codec} · ${sampleRateKhz} kHz · ~${bitrateKbps} kbps`}
              </p>

              <p className="mt-3 text-xs leading-relaxed text-white/60">
                {isHiRes
                  ? 'Hi-Res Lossless audio provides up to 24-bit, 192 kHz resolution. Bit-for-bit studio master accuracy with zero dynamic range loss.'
                  : isFlac
                    ? 'Lossless audio compression preserves all original audio data up to 16-bit, 44.1 kHz (CD Quality) with pure studio fidelity.'
                    : 'Standard high-efficiency streaming audio optimized for fast buffering and reliable stereo playback.'}
              </p>
            </div>

            {/* Apple Style Technical Inset Details List */}
            <div className="mt-5 rounded-2xl bg-white/5 border border-white/8 p-3 text-xs divide-y divide-white/6">
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
              className="mt-5 w-full rounded-full bg-white/15 hover:bg-white/20 active:bg-white/10 py-3 text-sm font-semibold text-white transition-all duration-120 cursor-pointer"
            >
              Done
            </button>
          </div>
        </div>
      )}
    </>
  );
}
