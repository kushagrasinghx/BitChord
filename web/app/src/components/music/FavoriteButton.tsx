import { StarFilledIcon, StarIcon } from '@/components/icons/Transport';
import { useLikeStatus, useSetLiked, useSignedIn } from '@/data/music/queries';
import { toastResult } from '@/state/toasts';

interface FavoriteButtonProps {
  videoId: string | null;
  className?: string;
  size?: number;
}

/**
 * Apple's Favorite star, backed by YouTube Music's like (a liked song joins
 * Liked Music). Only shown signed in; the state is read from the track's
 * watch page, as YouTube's own player reads it.
 */
export function FavoriteButton({ videoId, className, size = 18 }: FavoriteButtonProps) {
  const signedIn = useSignedIn();
  const status = useLikeStatus(videoId);
  const setLiked = useSetLiked();
  if (!signedIn || !videoId) return null;
  const liked = status.data === 'LIKE';
  return (
    <button
      className={className}
      aria-label={liked ? 'Unfavorite' : 'Favorite'}
      aria-pressed={liked}
      disabled={status.isPending}
      onClick={(event) => {
        event.stopPropagation();
        toastResult(setLiked.mutateAsync({ videoId, liked: !liked }), liked ? 'Removed from Favorites' : 'Added to Favorites', 'Couldn’t update Favorites');
      }}
    >
      {liked ? <StarFilledIcon size={size} /> : <StarIcon size={size} />}
    </button>
  );
}
