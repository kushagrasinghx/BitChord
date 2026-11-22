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