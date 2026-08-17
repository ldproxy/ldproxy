/* eslint-disable no-undef, no-underscore-dangle */
import React from "react";
import { createRoot } from "react-dom/client";
import CesiumMap from "@xtramaps/web-map-cesium-react";
import "@xtramaps/web-map-cesium-react/dist/index.css";

if (globalThis._map && globalThis._cesium && globalThis._map.container) {
  globalThis.CESIUM_BASE_URL = globalThis._cesium.assetsPrefix + process.env.CESIUM_PATH;

  // Unlike the old imperative `Cesium({ container, ... })` call (which attached the Viewer
  // directly to the Mustache-rendered container element), CesiumMap is a real React component
  // that creates and owns its own inner container div - it never reads a `container` prop.
  createRoot(document.getElementById(globalThis._map.container)).render(
    <React.StrictMode>
      <CesiumMap {...globalThis._map} {...globalThis._cesium} />
    </React.StrictMode>
  );
}
