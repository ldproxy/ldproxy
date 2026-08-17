import React from "react";

import MapLibre, { CanvasPlugin } from "@xtramaps/web-map-maplibre-react";
import "@xtramaps/web-map-maplibre-react/dist/index.css";
import Resizer from "./Resizer";
import type { Bounds } from "./Resizer/calc";
import "./style.css";

export interface MapSelectProps {
  bounds?: Bounds;
  backgroundUrl?: string | null;
  attribution?: string | null;
  onChange: (bounds: Bounds) => void;
}

const MapSelect = ({
  bounds = [
    [0, 0],
    [0, 0],
  ],
  backgroundUrl = null,
  attribution = null,
  onChange,
}: MapSelectProps) => {
  return (
    <MapLibre
      backgroundUrl={backgroundUrl ?? undefined}
      attribution={attribution ?? undefined}
      bounds={bounds}
      fitBoundsOptions={{ padding: 50, maxZoom: 16, animate: false }}
      showCompass={false}
    >
      <CanvasPlugin>
        <Resizer bounds={bounds} onChange={onChange} />
      </CanvasPlugin>
    </MapLibre>
  );
};

MapSelect.displayName = "MapSelect";

export default MapSelect;
