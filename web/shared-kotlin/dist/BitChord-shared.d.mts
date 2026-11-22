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