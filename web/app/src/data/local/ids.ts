/**
 * Local Music's ids, kept apart from the rest of the feature so the player,
 * the menus and the links can ask "is this a file?" without loading it.
 *
 * A file's track id is `local:` and a hash of its path under the chosen
 * folder, so a queue, the Replay or a party message can name it without
 * carrying the path. Android names a file by its content URI instead
 * (LocalMediaRepository.kt AudioColumns); a browser has nothing like one.
 * The grouping pages use LocalGroups.kt's own `local:group:` ids.
 */
export const LOCAL_PREFIX = 'local:';
export const LOCAL_GROUP_PREFIX = 'local:group:';
/** The Local Music folder's id on the phone (MainActivity.kt "local:all"), which its grouping ids carry. */
export const LOCAL_FOLDER_ID = 'local:all';

export const isLocalId = (id: string | null | undefined): boolean => !!id?.startsWith(LOCAL_PREFIX);
export const isLocalGroupId = (id: string | null | undefined): boolean => !!id?.startsWith(LOCAL_GROUP_PREFIX);

/** The page a grouping id opens on. */
export const localGroupHref = (id: string): string => `/library/local/${encodeURIComponent(id)}`;

/** cyrb53: a 53-bit string hash, plenty for one library's paths and stable across visits. */
export function localTrackId(path: string): string {
  let h1 = 0xdeadbeef;
  let h2 = 0x41c6ce57;
  for (let i = 0; i < path.length; i++) {
    const ch = path.charCodeAt(i);
    h1 = Math.imul(h1 ^ ch, 2654435761);
    h2 = Math.imul(h2 ^ ch, 1597334677);
  }
  h1 = Math.imul(h1 ^ (h1 >>> 16), 2246822507) ^ Math.imul(h2 ^ (h2 >>> 13), 3266489909);
  h2 = Math.imul(h2 ^ (h2 >>> 16), 2246822507) ^ Math.imul(h1 ^ (h1 >>> 13), 3266489909);
  return LOCAL_PREFIX + (4294967296 * (2097151 & h2) + (h1 >>> 0)).toString(36);
}
