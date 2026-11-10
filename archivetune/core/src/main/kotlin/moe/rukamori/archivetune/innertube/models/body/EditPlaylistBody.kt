/*
 * ArchiveTune (2026)
 * © Rukamori — github.com/rukamori
 * GPL-3.0 License | Contributors: see git history
 * Do not remove or alter this notice. - Per GPL-3.0 Section 4 & Section 5
 */

package moe.rukamori.archivetune.innertube.models.body

import kotlinx.serialization.Serializable
import moe.rukamori.archivetune.innertube.models.Context

@Serializable
data class EditPlaylistBody(
    val context: Context,
    val playlistId: String,
    val actions: List<Action>,
)

@Serializable
sealed class Action {
    @Serializable
    data class AddVideoAction(
        val action: String = "ACTION_ADD_VIDEO",
        val addedVideoId: String,
    ) : Action()

    @Serializable
    data class AddPlaylistAction(
        val action: String = "ACTION_ADD_PLAYLIST",
        val addedFullListId: String,
    ) : Action()

    @Serializable
    data class MoveVideoAction(
        val action: String = "ACTION_MOVE_VIDEO_BEFORE",
        val setVideoId: String,
        val movedSetVideoIdSuccessor: String?,
    ) : Action()

    @Serializable
    data class RemoveVideoAction(
        val action: String = "ACTION_REMOVE_VIDEO",
        val setVideoId: String,
        val removedVideoId: String,
    ) : Action()

    @Serializable
    data class RenamePlaylistAction(
        val action: String = "ACTION_SET_PLAYLIST_NAME",
        val playlistName: String,
    ) : Action()

    @Serializable
    data class SetCustomThumbnailAction(
        val action: String = "ACTION_SET_CUSTOM_THUMBNAIL",
        val addedCustomThumbnail: AddedCustomThumbnail,
    ) : Action() {
        @Serializable
        data class AddedCustomThumbnail(
            val imageKey: ImageKey = ImageKey(),
            val playlistScottyEncryptedBlobId: String,
        ) {
            @Serializable
            data class ImageKey(
                val name: String = "studio_square_thumbnail",
                val type: String = "PLAYLIST_IMAGE_TYPE_CUSTOM_THUMBNAIL",
            )
        }
    }

    @Serializable
    data class RemoveCustomThumbnailAction(
        val action: String = "ACTION_REMOVE_CUSTOM_THUMBNAIL",
        val deletedCustomThumbnail: DeletedCustomThumbnail = DeletedCustomThumbnail(),
    ) : Action() {
        @Serializable
        data class DeletedCustomThumbnail(
            val name: String = "studio_square_thumbnail",
            val type: String = "PLAYLIST_IMAGE_TYPE_CUSTOM_THUMBNAIL",
        )
    }
}
