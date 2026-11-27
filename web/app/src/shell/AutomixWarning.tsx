import { useCallback, type ReactNode } from 'react';
import { useRememberedConfirm } from '@/components/ConfirmDialog';
import { usePlayer } from '@/state/player';

/** Set once the user ticks "Don't show this again". */
const DISMISSED_KEY = 'bitchord:automix-memory-warning';

/**
 * Turning Automix on, through a warning about its memory use until the user
 * dismisses it for good (Settings → Crossfade, and the Up Next menu).
 * Analysing a new song runs essentia and two ONNX models
 * (playback/automix/analysis), measured at up to ~1 GB for about a minute;
 * between songs it uses no more than without Automix.
 *
 * Render `dialog` wherever `turnOn` is called from.
 */
export function useTurnOnAutomix(): { turnOn: () => void; dialog: ReactNode } {
  const enable = useCallback(() => usePlayer.getState().setAutomix(true), []);
  const { ask, dialog } = useRememberedConfirm(DISMISSED_KEY, enable, {
    title: 'Automix Uses More Memory',
    confirmLabel: 'Turn On Automix',
    children: (
      <>
        <p>
          Automix listens to each new song with on-device models to find its beats, key and vocals. While it does, for about a
          minute each time a new song comes up, BitChord can use up to around 1 GB of memory.
        </p>
        <p>The rest of the time it uses no more than usual. On a computer with little memory free, other apps may slow down meanwhile.</p>
      </>
    ),
  });

  const turnOn = useCallback(() => {
    if (!usePlayer.getState().automix) ask();
  }, [ask]);

  return { turnOn, dialog };
}
