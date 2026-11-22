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