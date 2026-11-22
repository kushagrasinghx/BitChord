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