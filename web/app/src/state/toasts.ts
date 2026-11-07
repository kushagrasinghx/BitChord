import { create } from 'zustand';

/** A short confirmation above the player ("Added to Road Trip"), Spotify-style. */
export interface Toast {
  id: number;
  message: string;
  /** Something went wrong: drawn in the destructive colour. */
  error?: boolean;
  /** A link at the end of the toast, such as View for the playlist a song was added to. */
  action?: { label: string; href: string };
}

/** How long a toast stays up; one with an action stays longer, to leave time to reach it. */
export const TOAST_MS = 3000;
export const TOAST_ACTION_MS = 5000;

interface ToastState {
  /** One at a time, like Spotify: a new toast replaces the one showing. */
  current: Toast | null;
  show: (toast: Omit<Toast, 'id'>) => void;
  dismiss: (id: number) => void;
}

let nextId = 1;

export const useToasts = create<ToastState>()((set) => ({
  current: null,
  show: (toast) => set({ current: { ...toast, id: nextId++ } }),
  // Only the toast asked about: a timer that outlived its toast must not close the next one.
  dismiss: (id) => set((state) => (state.current?.id === id ? { current: null } : state)),
}));

/** Shows a toast; callable from anywhere, including after the menu that asked has closed. */
export function toast(message: string, options: Omit<Toast, 'id' | 'message'> = {}): void {
  useToasts.getState().show({ message, ...options });
}

/** Waits for [work], then confirms with [success] or says [failure]. */
export function toastResult<T>(work: Promise<T>, success: string | Omit<Toast, 'id'>, failure: string): void {
  work.then(
    () => (typeof success === 'string' ? toast(success) : useToasts.getState().show(success)),
    () => toast(failure, { error: true }),
  );
}
