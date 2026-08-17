/* eslint-disable no-undef, no-underscore-dangle */
import React, { createRef } from "react";
import { createRoot } from "react-dom/client";
import OpenLayers from "@xtramaps/web-map-openlayers-react";
import "@xtramaps/web-map-openlayers-react/dist/index.css";

if (globalThis._map && globalThis._map.container) {
  const openLayersRef = createRef();
  // tiles.mustache calls this directly (Java-rendered TileMatrixSet switcher markup), so the
  // bridge has to keep existing under this exact name - only its implementation moves from an
  // internal component assignment to this ref indirection now that OpenLayers exposes the same
  // capability via forwardRef/useImperativeHandle instead.
  globalThis._map.setCurrentTileMatrixSet = (tms) =>
    openLayersRef.current?.setCurrentTileMatrixSet(tms);

  createRoot(document.getElementById(global._map.container)).render(
    <React.StrictMode>
      <OpenLayers ref={openLayersRef} {...globalThis._map} />
    </React.StrictMode>
  );
}
