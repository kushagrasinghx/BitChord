import { Filter, Folder, FolderLock, FolderX, RefreshCw } from 'lucide-react';
import { useEffect } from 'react';
import { allowAccess, chooseFolder, forgetFolder, rescan, startLocalLibrary, useLocalLibrary } from '@/data/local/library';
import { localMusicSupported, useLocalSettings } from '@/data/local/settings';
import { ButtonRow, Group, SettingsRow, Toggle } from './SettingsRows';

/**
 * Local Music (SettingsSheet.kt LOCAL_MUSIC): the folder, Rescan, and
 * Filter non-music audio. The phone's "Use all audio folders" has no
 * browser equivalent (a page can only read a folder it was handed), so the
 * folder can be removed instead. Where folders can't be opened at all the
 * page says so rather than offering a picker that isn't there.
 */
export function LocalMusicSettings() {
  const supported = localMusicSupported();
  const { folder, access, files, scanning, error } = useLocalLibrary();
  const filter = useLocalSettings((s) => s.filterNonMusic);
  const setFilter = useLocalSettings((s) => s.setFilterNonMusic);
  useEffect(() => {
    if (supported) void startLocalLibrary();
  }, [supported]);

  if (!supported) {
    return (
      <Group header="Local Music">
        <SettingsRow
          icon={Folder}
          title="Local music folder"
          detail="This browser can’t open folders. Local Music needs Chrome or Edge on a computer, or the BitChord app for Windows."
          keywords={['folder', 'offline', 'files', 'local']}
        />
      </Group>
    );
  }
  const keywords = ['folder', 'offline', 'files', 'local'];
  return (
    <Group header="Local Music" footer={error ?? undefined}>
      <ButtonRow icon={Folder} title="Local music folder" detail={folder ?? 'None chosen'} value={folder ? 'Change' : 'Choose'} keywords={keywords} onClick={() => void chooseFolder()} />
      {folder && access !== null && access !== 'granted' && (
        <ButtonRow
          icon={FolderLock}
          title={`Allow access to ${folder}`}
          detail="Access to this folder lapses after a restart."
          keywords={keywords}
          onClick={() => void allowAccess()}
        />
      )}
      {folder && (
        <ButtonRow
          icon={RefreshCw}
          title="Rescan"
          detail={scanning !== null ? `Scanning… ${scanning} files` : `${files.length} ${files.length === 1 ? 'file' : 'files'}`}
          keywords={[...keywords, 'scan', 'refresh']}
          onClick={() => void rescan()}
        />
      )}
      {folder && (
        <ButtonRow
          icon={FolderX}
          title="Remove folder"
          detail="Forgets the folder and its index. Your files aren’t touched."
          keywords={keywords}
          onClick={() => void forgetFolder()}
        />
      )}
      <Toggle
        icon={Filter}
        label="Filter non-music audio"
        detail="Hides clips under 30 seconds, WAV files, voice notes, recordings and system sounds"
        keywords={['podcast', 'recording']}
        checked={filter}
        onChange={setFilter}
      />
    </Group>
  );
}
