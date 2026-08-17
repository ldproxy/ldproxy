import React from "react";
import { createRoot } from "react-dom/client";
import CesiumMap, { type CesiumMapProps } from "@xtramaps/web-map-cesium-react";
import "@xtramaps/web-map-cesium-react/dist/index.css";

if (globalThis._map && globalThis._cesium && globalThis._map.container) {
  globalThis.CESIUM_BASE_URL = globalThis._cesium.assetsPrefix + process.env.CESIUM_PATH;

  // CesiumMapProps.backgroundUrl is typed as required, but MapClient (Java) only ever sets
  // it as an Optional<String> - Cesium's rendering path (imagery + terrain, no vector style)
  // always supplies one in practice, so this cast reflects a real runtime guarantee the
  // xtramaps package's own type doesn't express, not a loosening of the actual contract.
  const props = { ...globalThis._map, ...globalThis._cesium } as unknown as CesiumMapProps;

  // Unlike the old imperative `Cesium({ container, ... })` call (which attached the Viewer
  // directly to the Mustache-rendered container element), CesiumMap is a real React component
  // that creates and owns its own inner container div - it never reads a `container` prop.
  createRoot(document.getElementById(globalThis._map.container)!).render(
    <React.StrictMode>
      <CesiumMap {...props} />
    </React.StrictMode>,
  );
}
