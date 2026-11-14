export type AudioQualityTier = 'LOSSLESS' | 'HIGH' | 'MEDIUM' | 'LOW';

export interface AddonManifest {
  id?: string;
  name: string;
  version?: string;
  description?: string;
  resources?: string[];
  bitchordAuth?: boolean;
  author?: string;
  endpoints?: {
    search?: string;
    stream?: string;
    health?: string;
  };
  streamEndpoint?: string;
  streamUrl?: string;
  searchEndpoint?: string;
}

export interface ConfiguredAddon {
  id: string;
  name: string;
  manifestUrl: string;
  baseUrl: string;
  streamEndpoint: string;
  searchEndpoint?: string;
  healthEndpoint?: string;
  enabled: boolean;
  preferredQuality: AudioQualityTier;
  health: 'online' | 'offline' | 'checking';
  pingMs?: number;
  lastChecked?: number;
}

export interface ResolvedAddonStream {
  streamUrl: string;
  addonName: string;
  format: string;
  codec?: string;
  bitrate?: number;
}
