/* eslint-disable no-undef, no-underscore-dangle */
import React from "react";
import { createRoot } from "react-dom/client";
import OpenLayers from "../../components/OpenLayers";

if (globalThis._map && globalThis._map.container) {
  createRoot(document.getElementById(global._map.container)).render(
    <React.StrictMode>
      <OpenLayers {...globalThis._map} />
    </React.StrictMode>
  );
}
