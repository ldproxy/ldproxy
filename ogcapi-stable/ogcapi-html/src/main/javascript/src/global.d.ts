/// <reference types="vite/client" />

import type { LayerControlEntry } from "@xtramaps/layer-control-maplibre";

// Ad-hoc globals set by Java-rendered inline <script> tags (one per app entry, e.g.
// mapClient.mustache/app-filtereditor.mustache) before the corresponding bundle runs. Every
// app entry spreads its global directly as props onto its root component, so each shape here
// mirrors that component's own props (plus the DOM element id used as the render target).
// `global.x` (as opposed to `globalThis.x`) resolves the same way at runtime: `vite.config.js`
// replaces the bare `global` identifier with `globalThis` at build time (see `define`).

export interface TranslationBundle {
  language: string;
  translations: Record<string, string>;
}

export interface FilterEditorGlobal extends TranslationBundle {
  container: string;
  backgroundUrl?: string;
  attribution?: string;
  [key: string]: unknown;
}

export interface SortingEditorGlobal extends TranslationBundle {
  sortingcontainer: string;
  [key: string]: unknown;
}

export interface CrsSelectorGlobal extends TranslationBundle {
  container: string;
  [key: string]: unknown;
}

export interface LimitSelectorGlobal extends TranslationBundle {
  container: string;
  limitOptions?: number[];
  allowCustomLimit?: boolean;
  defaultLimit?: number | null;
  [key: string]: unknown;
}

export interface MapGlobal {
  container: string;
  backgroundUrl?: string;
  attribution?: string;
  layerGroupControl?: LayerControlEntry[];
  [key: string]: unknown;
}

export interface CesiumGlobal {
  assetsPrefix: string;
  extent?: { minLon: number; minLat: number; maxLon: number; maxLat: number };
  tileset?: Record<string, unknown>;
  accessToken?: string | null;
  terrainProvider?: Record<string, unknown>;
  [key: string]: unknown;
}

declare global {
  // eslint-disable-next-line no-var
  var _filter: FilterEditorGlobal | undefined;
  // eslint-disable-next-line no-var
  var _sortingfilter: SortingEditorGlobal | undefined;
  // eslint-disable-next-line no-var
  var _crs_selector: CrsSelectorGlobal | undefined;
  // eslint-disable-next-line no-var
  var _limit_selector: LimitSelectorGlobal | undefined;
  // eslint-disable-next-line no-var
  var _map: MapGlobal | undefined;
  // eslint-disable-next-line no-var
  var _cesium: CesiumGlobal | undefined;
  // eslint-disable-next-line no-var
  var CESIUM_BASE_URL: string;
  // eslint-disable-next-line no-var
  var $: JQueryStatic | undefined;

  interface Window {
    _filter?: FilterEditorGlobal;
    _sortingfilter?: SortingEditorGlobal;
    _crs_selector?: CrsSelectorGlobal;
    _limit_selector?: LimitSelectorGlobal;
    _map?: MapGlobal;
    _cesium?: CesiumGlobal;
  }
}

