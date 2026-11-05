import { useEffect } from 'react';
import { usePlayer } from '@/state/player';
import { APP_TITLE, documentTitle } from './documentTitle';

/** Keeps the browser tab in step with what's playing. */
export function useDocumentTitle() {
  const title = usePlayer((s) => s.nowPlaying?.title ?? null);
  useEffect(() => {
    document.title = documentTitle(title ? { title } : null);
    return () => {
      document.title = APP_TITLE;
    };
  }, [title]);
}
