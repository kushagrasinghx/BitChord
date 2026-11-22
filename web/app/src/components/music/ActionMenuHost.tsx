import { Menu } from '@/components/Menu';
import { useActionMenu } from './actionMenu';
import { BrowseMenuItems } from './BrowseMenu';
import { PlaylistPicker } from './PlaylistPicker';
import { SongInfo } from './SongCredits';
import { TrackMenuItems } from './TrackMenu';

/**
 * Draws whichever song or release menu is up (actionMenu.ts), and the dialog
 * one of its rows opened once the menu has gone: one host for every ⋯ and
 * right-click in the app, so there's only ever one.
 */
export function ActionMenuHost() {
  const menu = useActionMenu((s) => s.menu);
  const dialog = useActionMenu((s) => s.dialog);
  const close = useActionMenu((s) => s.close);
  const closeDialog = useActionMenu((s) => s.closeDialog);
  const { target } = menu ?? {};
  return (
    <>
      {menu && target && (
        <Menu
          key={menu.key}
          anchor={menu.anchor}
          placement={menu.placement}
          point={menu.point}
          lift={menu.lift}
          onClose={close}
          label={target.kind === 'track' ? target.track.title : target.item.title}
        >
          {target.kind === 'track' ? <TrackMenuItems track={target.track} player={target.player} /> : <BrowseMenuItems item={target.item} />}
        </Menu>
      )}
      {dialog?.kind === 'playlists' && <PlaylistPicker tracks={dialog.tracks} onClose={closeDialog} />}
      {dialog?.kind === 'info' && <SongInfo track={dialog.track} onClose={closeDialog} />}
    </>
  );
}
