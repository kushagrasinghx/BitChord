import { Fragment } from 'react';
import { create } from 'zustand';
import { Dialog } from '@/components/Dialog';

/** Whether the shortcuts sheet is up: opened by "?" (useKeyboardShortcuts) or from anywhere that offers it. */
export const useShortcutsSheet = create<{ open: boolean; toggle: () => void; setOpen: (open: boolean) => void }>((set) => ({
  open: false,
  toggle: () => set((s) => ({ open: !s.open })),
  setOpen: (open) => set({ open }),
}));

// The command key as this computer names it.
const MOD = /Mac|iPhone|iPad/.test(navigator.userAgent) ? '⌘' : 'Ctrl';

/** Each shortcut as its keys (alternatives separated by "or") and what it does; kept in step with useKeyboardShortcuts. */
const GROUPS: { title: string; rows: [keys: string[][], label: string][] }[] = [
  {
    title: 'Playback',
    rows: [
      [[['Space']], 'Play or pause'],
      [[['←'], ['→']], 'Back or forward 10 seconds'],
      [[['Shift', '←'], ['Shift', '→']], 'Previous or next song'],
      [[[MOD, '←'], [MOD, '→']], 'Previous or next song'],
      [[[MOD, '↑'], [MOD, '↓']], 'Volume up or down'],
      [[['M']], 'Mute or unmute'],
    ],
  },
  {
    title: 'View',
    rows: [
      [[['L']], 'Show or hide lyrics'],
      [[['Q']], 'Show or hide the queue'],
      [[['Esc']], 'Close the player or side panel'],
      [[['/'], [MOD, 'F']], 'Search'],
      [[['Alt', '←']], 'Back'],
      [[['?']], 'Keyboard shortcuts'],
    ],
  },
];

const key = 'inline-grid h-6 min-w-6 place-items-center rounded-[6px] bg-quinary px-1.5 type-footnote text-primary';

function Keys({ keys }: { keys: string[][] }) {
  return (
    <span className="flex flex-none items-center gap-1.5 type-footnote text-secondary">
      {keys.map((combo, i) => (
        <Fragment key={i}>
          {i > 0 && <span>or</span>}
          <span className="flex items-center gap-1">
            {combo.map((k) => (
              <kbd key={k} className={key}>
                {k}
              </kbd>
            ))}
          </span>
        </Fragment>
      ))}
    </span>
  );
}

/** The keyboard shortcuts, in the app's dialog: what each does, then its keys. */
function ShortcutsSheet({ onClose }: { onClose: () => void }) {
  return (
    <Dialog title="Keyboard Shortcuts" onClose={onClose}>
      {GROUPS.map((group) => (
        <section key={group.title} className="pt-3 first:pt-1">
          <h3 className="pb-1 type-callout-emphasized text-secondary">{group.title}</h3>
          <dl className="flex flex-col">
            {group.rows.map(([keys, label]) => (
              <div key={label + keys.flat().join()} className="flex min-h-9 items-center justify-between gap-4 border-b-[0.5px] border-b-divider last:border-b-0">
                <dt className="type-body">{label}</dt>
                <dd>
                  <Keys keys={keys} />
                </dd>
              </div>
            ))}
          </dl>
        </section>
      ))}
    </Dialog>
  );
}

/** Mounted once in the shell (beside the player bar's menu host), so the sheet can open from any page. */
export function ShortcutsHost() {
  const open = useShortcutsSheet((s) => s.open);
  const setOpen = useShortcutsSheet((s) => s.setOpen);
  return open ? <ShortcutsSheet onClose={() => setOpen(false)} /> : null;
}
