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