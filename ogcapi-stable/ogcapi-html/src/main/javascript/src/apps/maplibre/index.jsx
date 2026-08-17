/* eslint-disable no-undef, no-underscore-dangle */
import React from "react";
import { createRoot } from "react-dom/client";
import "core-js";
import MapLibre from "@xtramaps/web-map-maplibre-react";
import "@xtramaps/web-map-maplibre-react/dist/index.css";
import { LayerControl } from "@xtramaps/layer-control-maplibre-react";

if (globalThis._map && globalThis._map.container) {
  const { layerGroupControl, ...mapProps } = globalThis._map;
  createRoot(document.getElementById(global._map.container)).render(
    <React.StrictMode>
      <MapLibre {...mapProps}>
        {layerGroupControl && layerGroupControl.length > 0 && (
          <LayerControl entries={layerGroupControl} />
        )}
      </MapLibre>
    </React.StrictMode>
  );
}
