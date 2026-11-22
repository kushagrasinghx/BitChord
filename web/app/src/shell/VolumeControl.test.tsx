// @vitest-environment happy-dom
import { act } from 'react';
import { createRoot } from 'react-dom/client';
import { afterEach, describe, expect, it, vi } from 'vitest';
import { create } from 'zustand';

const player = create<{ volume: number; muted: boolean; setVolume: (v: number) => void; toggleMute: () => void }>()((set) => ({
  volume: 0.5,
  muted: false,
  setVolume: (volume) => set({ volume, muted: false }),
  toggleMute: () => set((s) => ({ muted: !s.muted })),
}));

vi.mock('@/state/player', () => ({ usePlayer: player }));
vi.mock('@/components/icons/Transport', () => ({ VolumeIcon: () => null }));

const { VolumeControl } = await import('./VolumeControl');
(globalThis as { IS_REACT_ACT_ENVIRONMENT?: boolean }).IS_REACT_ACT_ENVIRONMENT = true;

function mount() {
  const host = document.createElement('div');
  document.body.append(host);
  const root = createRoot(host);
  act(() => root.render(<VolumeControl open={false} onOpen={() => {}} className="" />));
  const wheel = (deltaY: number) => act(() => void host.firstElementChild!.dispatchEvent(new WheelEvent('wheel', { deltaY, cancelable: true })));
  return { wheel, unmount: () => act(() => root.unmount()) };
}

describe('volume wheel', () => {
  let unmount: (() => void) | null = null;
  afterEach(() => unmount?.());

  it('stays silent when muted and wheeled down', () => {
    player.setState({ volume: 0.5, muted: true });
    const control = mount();
    unmount = control.unmount;
    control.wheel(100);
    expect(player.getState()).toMatchObject({ volume: 0.5, muted: true });
  });

  it('unmutes from silence, one step up', () => {
    player.setState({ volume: 0.5, muted: true });
    const control = mount();
    unmount = control.unmount;
    control.wheel(-100);
    expect(player.getState()).toMatchObject({ volume: 0.05, muted: false });
  });
});
