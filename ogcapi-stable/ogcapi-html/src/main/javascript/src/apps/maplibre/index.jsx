/* eslint-disable no-undef, no-underscore-dangle */
import React from "react";
import { createRoot } from "react-dom/client";
import "core-js";
import MapLibre from "../../components/MapLibre";

if (globalThis._map && globalThis._map.container) {
  createRoot(document.getElementById(global._map.container)).render(
    <React.StrictMode>
      <MapLibre {...globalThis._map} />
    </React.StrictMode>
  );
}
