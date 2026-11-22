type Nullable<T> = T | null | undefined
declare function KtSingleton<T>(): T & (abstract new() => any);
export declare class BestPartLine {
    constructor(timeMs: number, text: string);
    get timeMs(): number;
    get text(): string;
}
export declare namespace BestPartLine {
    /** @deprecated $metadata$ is used for internal purposes, please don't use it in your code, because it can be removed at any moment */
    namespace $metadata$ {
        const constructor: abstract new () => BestPartLine;
    }
}
export declare class BestPartPick {
    constructor(startMs: number, source: string);
    get startMs(): number;
    get source(): string;
}
export declare namespace BestPartPick {
    /** @deprecated $metadata$ is used for internal purposes, please don't use it in your code, because it can be removed at any moment */
    namespace $metadata$ {
        const constructor: abstract new () => BestPartPick;
    }
}
export declare function bestPart(nextResponse: Nullable<string>, lines: Array<BestPartLine>, durationMs: number): BestPartPick;
export declare function bucketPlus(a: Int8Array, b: Int8Array): Int8Array;
export declare function bucketMinus(a: Int8Array, b: Int8Array): Nullable<Int8Array>;
export declare class MonthHash {
    constructor(month: string, hash: Nullable<string>);
    get month(): string;
    get hash(): Nullable<string>;
}
export declare namespace MonthHash {
    /** @deprecated $metadata$ is used for internal purposes, please don't use it in your code, because it can be removed at any moment */
    namespace $metadata$ {
        const constructor: abstract new () => MonthHash;
    }
}
export declare class SyncPushPlan {
    constructor(upload: Array<string>, _delete: Array<string>);
    get upload(): Array<string>;
    get delete(): Array<string>;
}
export declare namespace SyncPushPlan {
    /** @deprecated $metadata$ is used for internal purposes, please don't use it in your code, because it can be removed at any moment */
    namespace $metadata$ {
        const constructor: abstract new () => SyncPushPlan;
    }
}
export declare function syncPush(local: Array<MonthHash>, synced: Array<MonthHash>, remote: Array<string>): SyncPushPlan;
export declare class SyncPullPlan {
    constructor(download: Array<string>, drop: Array<string>);
    get download(): Array<string>;
    get drop(): Array<string>;
}
export declare namespace SyncPullPlan {
    /** @deprecated $metadata$ is used for internal purposes, please don't use it in your code, because it can be removed at any moment */
    namespace $metadata$ {
        const constructor: abstract new () => SyncPullPlan;
    }
}
export declare function syncPull(listed: Array<string>, remoteMd5: Array<MonthHash>, held: Array<MonthHash>): SyncPullPlan;
export declare function syncSupersededBy(installId: string, adoptedManifestAt: Nullable<string>, manifestJson: Nullable<string>, manifestAt: Nullable<string>): boolean;
export declare class WebPref {
    constructor(key: string, type: string, value: Nullable<string>, values: Array<string>);
    get key(): string;
    get type(): string;
    get value(): Nullable<string>;
    get values(): Array<string>;
}
export declare namespace WebPref {
    /** @deprecated $metadata$ is used for internal purposes, please don't use it in your code, because it can be removed at any moment */
    namespace $metadata$ {
        const constructor: abstract new () => WebPref;
    }
}
export declare class WebManifest {
    constructor(schema: number, deviceId: string, name: string, ownerInstall: string, updatedAt: number, versionName: string, months: Array<MonthHash>, settings: Array<WebPref>);
    get schema(): number;
    get deviceId(): string;
    get name(): string;
    get ownerInstall(): string;
    get updatedAt(): number;
    get versionName(): string;
    get months(): Array<MonthHash>;
    get settings(): Array<WebPref>;
}
export declare namespace WebManifest {
    /** @deprecated $metadata$ is used for internal purposes, please don't use it in your code, because it can be removed at any moment */
    namespace $metadata$ {
        const constructor: abstract new () => WebManifest;
    }
}
export declare function decodeManifest(text: string): WebManifest;
export declare function encodeManifest(deviceId: string, name: string, ownerInstall: string, updatedAt: number, versionName: string, months: Array<MonthHash>, settings: Array<WebPref>): string;
export declare class MixMood {
    constructor(key: string, name: string, items: Array<number>);
    get key(): string;
    get name(): string;
    get items(): Array<number>;
}
export declare namespace MixMood {
    /** @deprecated $metadata$ is used for internal purposes, please don't use it in your code, because it can be removed at any moment */
    namespace $metadata$ {
        const constructor: abstract new () => MixMood;
    }
}
export declare class MixGroups {
    constructor(stations: Array<number>, yours: Array<number>, moods: Array<MixMood>);
    get stations(): Array<number>;
    get yours(): Array<number>;
    get moods(): Array<MixMood>;
}
export declare namespace MixGroups {
    /** @deprecated $metadata$ is used for internal purposes, please don't use it in your code, because it can be removed at any moment */
    namespace $metadata$ {
        const constructor: abstract new () => MixGroups;
    }
}
export declare function groupMixes(ids: Array<string>, titles: Array<string>, englishTitles: Array<string>): MixGroups;
export declare function songSorts(): Array<string>;
export declare function detailSortOrder(titles: Array<string>, sort: string): Array<number>;
export declare class LyricClock {
    constructor(startMs: number);
    get displayedMs(): number;
    restart(): void;
    observe(nowMs: number, positionMs: number, sampledAtMs: number, discontinuity: number, isPlaying: boolean): Nullable<number>;
    frame(nowMs: number, positionMs: number, sampledAtMs: number, discontinuity: number, isPlaying: boolean): number;
}
export declare namespace LyricClock {
    /** @deprecated $metadata$ is used for internal purposes, please don't use it in your code, because it can be removed at any moment */
    namespace $metadata$ {
        const constructor: abstract new () => LyricClock;
    }
}
export declare function fitsOnShareCard(taken: number, text: Nullable<string>): boolean;
export declare class ShareSourceLine {
    constructor(timeMs: number, text: string, sungUntilMs: Nullable<number>);
    get timeMs(): number;
    get text(): string;
    get sungUntilMs(): Nullable<number>;
}
export declare namespace ShareSourceLine {
    /** @deprecated $metadata$ is used for internal purposes, please don't use it in your code, because it can be removed at any moment */
    namespace $metadata$ {
        const constructor: abstract new () => ShareSourceLine;
    }
}
export declare class ShareLine {
    constructor(text: string, isGap: boolean, startMs: Nullable<number>, endMs: Nullable<number>, source: number);
    get text(): string;
    get isGap(): boolean;
    get startMs(): Nullable<number>;
    get endMs(): Nullable<number>;
    get source(): number;
}
export declare namespace ShareLine {
    /** @deprecated $metadata$ is used for internal purposes, please don't use it in your code, because it can be removed at any moment */
    namespace $metadata$ {
        const constructor: abstract new () => ShareLine;
    }
}
export declare function pickShareLines(lines: Array<ShareSourceLine>, picks: Array<number>): Array<ShareLine>;
export declare class ShareSegment {
    constructor(startMs: number, endMs: number);
    get startMs(): number;
    get endMs(): number;
}
export declare namespace ShareSegment {
    /** @deprecated $metadata$ is used for internal purposes, please don't use it in your code, because it can be removed at any moment */
    namespace $metadata$ {
        const constructor: abstract new () => ShareSegment;
    }
}
export declare class ShareVideoTimeline {
    private constructor();
    get durationMs(): number;
    get segments(): Array<ShareSegment>;
    trackMsAt(outputMs: number): number;
}
export declare namespace ShareVideoTimeline {
    /** @deprecated $metadata$ is used for internal purposes, please don't use it in your code, because it can be removed at any moment */
    namespace $metadata$ {
        const constructor: abstract new () => ShareVideoTimeline;
    }
}
export declare function shareTimeline(lines: Array<ShareLine>): Nullable<ShareVideoTimeline>;
export declare class BestPartHeat {
    constructor(segmentMs: number, intensity: Float64Array);
    get segmentMs(): number;
    get intensity(): Float64Array;
}
export declare namespace BestPartHeat {
    /** @deprecated $metadata$ is used for internal purposes, please don't use it in your code, because it can be removed at any moment */
    namespace $metadata$ {
        const constructor: abstract new () => BestPartHeat;
    }
}
export declare function bestPartHeatmap(nextResponse: string): Nullable<BestPartHeat>;
export declare function bestPartFromHeat(heat: Nullable<BestPartHeat>, lines: Array<BestPartLine>, durationMs: number): BestPartPick;
export declare class AutoMixWindow {
    constructor(startSeconds: number, endSeconds: number);
    get startSeconds(): number;
    get endSeconds(): number;
}
export declare namespace AutoMixWindow {
    /** @deprecated $metadata$ is used for internal purposes, please don't use it in your code, because it can be removed at any moment */
    namespace $metadata$ {
        const constructor: abstract new () => AutoMixWindow;
    }
}
export declare function autoMixArtworkWindow(swap: number, end: number, preparedForSeconds: number): Nullable<AutoMixWindow>;
export declare function autoMixRevealProgress(progress: number): number;
export declare class AutoMixMaskTuning {
    constructor(noiseScale: number, noiseSoftness: number, noiseLow: number, noiseHigh: number, noiseSeedSpan: number, noiseOctaves: number, blurStartDp: number, blurEndProgress: number);
    get noiseScale(): number;
    get noiseSoftness(): number;
    get noiseLow(): number;
    get noiseHigh(): number;
    get noiseSeedSpan(): number;
    get noiseOctaves(): number;
    get blurStartDp(): number;
    get blurEndProgress(): number;
}
export declare namespace AutoMixMaskTuning {
    /** @deprecated $metadata$ is used for internal purposes, please don't use it in your code, because it can be removed at any moment */
    namespace $metadata$ {
        const constructor: abstract new () => AutoMixMaskTuning;
    }
}
export declare function autoMixMaskTuning(): AutoMixMaskTuning;
export declare function youTubeThumbnailFallback(url: string): Nullable<string>;
export declare function queueHistoryTrimCount(currentIndex: number): number;
export declare function lastPlayedWindow(size: number, index: number): Array<number>;
export declare class SleepTimerOptions {
    constructor(presets: Array<number>, stepMinutes: number, maxMinutes: number, fadeOutMs: number);
    get presets(): Array<number>;
    get stepMinutes(): number;
    get maxMinutes(): number;
    get fadeOutMs(): number;
}
export declare namespace SleepTimerOptions {
    /** @deprecated $metadata$ is used for internal purposes, please don't use it in your code, because it can be removed at any moment */
    namespace $metadata$ {
        const constructor: abstract new () => SleepTimerOptions;
    }
}
export declare function sleepTimerOptions(): SleepTimerOptions;
export declare function sleepFadeVolume(remainingMs: number, fadeDurationMs: number, fadeOutEnabled: boolean): number;
export declare class ReplayStep {
    constructor(playedMs: number, countsAsPlay: boolean, flush: boolean);
    get playedMs(): number;
    get countsAsPlay(): boolean;
    get flush(): boolean;
}
export declare namespace ReplayStep {
    /** @deprecated $metadata$ is used for internal purposes, please don't use it in your code, because it can be removed at any moment */
    namespace $metadata$ {
        const constructor: abstract new () => ReplayStep;
    }
}
export declare class ReplayTally {
    constructor();
    sample(id: string, now: number, lengthMs: number): Nullable<ReplayStep>;
    stop(): void;
}
export declare namespace ReplayTally {
    /** @deprecated $metadata$ is used for internal purposes, please don't use it in your code, because it can be removed at any moment */
    namespace $metadata$ {
        const constructor: abstract new () => ReplayTally;
    }
}
export declare class ReplayTrack {
    constructor(videoId: string, title: string, artist: string, album: Nullable<string>, albumId: Nullable<string>, artistId: Nullable<string>, art: Nullable<string>);
    get videoId(): string;
    get title(): string;
    get artist(): string;
    get album(): Nullable<string>;
    get albumId(): Nullable<string>;
    get artistId(): Nullable<string>;
    get art(): Nullable<string>;
}
export declare namespace ReplayTrack {
    /** @deprecated $metadata$ is used for internal purposes, please don't use it in your code, because it can be removed at any moment */
    namespace $metadata$ {
        const constructor: abstract new () => ReplayTrack;
    }
}
export declare class ReplayMonth {
    constructor(month: string, json: Nullable<string>);
    get month(): string;
    record(track: ReplayTrack, playedMs: number, countsAsPlay: boolean, now: number, hour: number, dayOfMonth: number): boolean;
    json(): string;
}
export declare namespace ReplayMonth {
    /** @deprecated $metadata$ is used for internal purposes, please don't use it in your code, because it can be removed at any moment */
    namespace $metadata$ {
        const constructor: abstract new () => ReplayMonth;
    }
}
export declare class ReplaySong {
    constructor(videoId: string, title: string, artist: string, art: Nullable<string>, album: Nullable<string>, albumId: Nullable<string>, artistId: Nullable<string>, ms: number, plays: number);
    get videoId(): string;
    get title(): string;
    get artist(): string;
    get art(): Nullable<string>;
    get album(): Nullable<string>;
    get albumId(): Nullable<string>;
    get artistId(): Nullable<string>;
    get ms(): number;
    get plays(): number;
}
export declare namespace ReplaySong {
    /** @deprecated $metadata$ is used for internal purposes, please don't use it in your code, because it can be removed at any moment */
    namespace $metadata$ {
        const constructor: abstract new () => ReplaySong;
    }
}
export declare class ReplayRow {
    constructor(title: string, subtitle: Nullable<string>, art: Nullable<string>, browseId: Nullable<string>, ms: number, plays: number);
    get title(): string;
    get subtitle(): Nullable<string>;
    get art(): Nullable<string>;
    get browseId(): Nullable<string>;
    get ms(): number;
    get plays(): number;
}
export declare namespace ReplayRow {
    /** @deprecated $metadata$ is used for internal purposes, please don't use it in your code, because it can be removed at any moment */
    namespace $metadata$ {
        const constructor: abstract new () => ReplayRow;
    }
}
export declare class ReplayCharts {
    constructor(songs: Array<ReplaySong>, artists: Array<ReplayRow>, albums: Array<ReplayRow>, hours: Array<number>, busiestDay: Nullable<string>, busiestDayMs: number, totalMs: number, totalPlays: number, since: Nullable<string>);
    get songs(): Array<ReplaySong>;
    get artists(): Array<ReplayRow>;
    get albums(): Array<ReplayRow>;
    get hours(): Array<number>;
    get busiestDay(): Nullable<string>;
    get busiestDayMs(): number;
    get totalMs(): number;
    get totalPlays(): number;
    get since(): Nullable<string>;
}
export declare namespace ReplayCharts {
    /** @deprecated $metadata$ is used for internal purposes, please don't use it in your code, because it can be removed at any moment */
    namespace $metadata$ {
        const constructor: abstract new () => ReplayCharts;
    }
}
export declare function replayCharts(buckets: Array<string>, weekFrom: Nullable<string>, weekTo: Nullable<string>): ReplayCharts;
export declare class ReplayMonthTop {
    constructor(month: string, song: ReplaySong);
    get month(): string;
    get song(): ReplaySong;
}
export declare namespace ReplayMonthTop {
    /** @deprecated $metadata$ is used for internal purposes, please don't use it in your code, because it can be removed at any moment */
    namespace $metadata$ {
        const constructor: abstract new () => ReplayMonthTop;
    }
}
export declare class ReplayYearStory {
    constructor(charts: ReplayCharts, dayKeys: Array<string>, dayMs: Array<number>, monthTops: Array<ReplayMonthTop>, busiestDaySong: Nullable<ReplaySong>, personality: Nullable<string>, slides: Array<string>, clips: Array<Nullable<ReplaySong>>);
    get charts(): ReplayCharts;
    get dayKeys(): Array<string>;
    get dayMs(): Array<number>;
    get monthTops(): Array<ReplayMonthTop>;
    get busiestDaySong(): Nullable<ReplaySong>;
    get personality(): Nullable<string>;
    get slides(): Array<string>;
    get clips(): Array<Nullable<ReplaySong>>;
}
export declare namespace ReplayYearStory {
    /** @deprecated $metadata$ is used for internal purposes, please don't use it in your code, because it can be removed at any moment */
    namespace $metadata$ {
        const constructor: abstract new () => ReplayYearStory;
    }
}
export declare function replayYear(months: Array<Array<string>>): Nullable<ReplayYearStory>;
export declare class SongCreditGroup {
    constructor(role: string, names: Array<string>);
    get role(): string;
    get names(): Array<string>;
}
export declare namespace SongCreditGroup {
    /** @deprecated $metadata$ is used for internal purposes, please don't use it in your code, because it can be removed at any moment */
    namespace $metadata$ {
        const constructor: abstract new () => SongCreditGroup;
    }
}
export declare class CreditsBrowse {
    constructor(browseId: string, params: Nullable<string>);
    get browseId(): string;
    get params(): Nullable<string>;
}
export declare namespace CreditsBrowse {
    /** @deprecated $metadata$ is used for internal purposes, please don't use it in your code, because it can be removed at any moment */
    namespace $metadata$ {
        const constructor: abstract new () => CreditsBrowse;
    }
}
export declare function songCredits(browseResponse: string): Array<SongCreditGroup>;
export declare function creditsBrowse(nextResponse: string, videoId: string): Nullable<CreditsBrowse>;
export declare function artworkAt(url: Nullable<string>, px: number): Nullable<string>;
export declare class PlaylistMove {
    constructor(setVideoId: string, before: Nullable<string>);
    get setVideoId(): string;
    get before(): Nullable<string>;
}
export declare namespace PlaylistMove {
    /** @deprecated $metadata$ is used for internal purposes, please don't use it in your code, because it can be removed at any moment */
    namespace $metadata$ {
        const constructor: abstract new () => PlaylistMove;
    }
}
export declare function playlistMoves(current: Array<string>, target: Array<string>): Array<PlaylistMove>;
export declare class SearchFilterOption {
    constructor(id: string, label: string, params: Nullable<string>, requiresAuth: boolean);
    get id(): string;
    get label(): string;
    get params(): Nullable<string>;
    get requiresAuth(): boolean;
}
export declare namespace SearchFilterOption {
    /** @deprecated $metadata$ is used for internal purposes, please don't use it in your code, because it can be removed at any moment */
    namespace $metadata$ {
        const constructor: abstract new () => SearchFilterOption;
    }
}
export declare function searchFilters(): Array<SearchFilterOption>;