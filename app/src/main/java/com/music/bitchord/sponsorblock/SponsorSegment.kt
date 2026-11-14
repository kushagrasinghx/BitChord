package com.music.bitchord.sponsorblock

/**
 * Parsed segment returned by the SponsorBlock API.
 *
 * @param uuid Unique ID of the segment submission.
 * @param category The category identifier matching [SponsorBlockCategory].
 * @param startMs Start time in milliseconds.
 * @param endMs End time in milliseconds.
 */
data class SponsorSegment(
    val uuid: String,
    val category: SponsorBlockCategory,
    val startMs: Long,
    val endMs: Long,
) {
    /** True if [positionMs] falls inside this segment. */
    fun contains(positionMs: Long): Boolean = positionMs in startMs until endMs
}
