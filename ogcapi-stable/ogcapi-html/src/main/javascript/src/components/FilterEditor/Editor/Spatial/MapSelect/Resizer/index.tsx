import React, { useEffect, useRef, useState } from "react";
import type * as maplibregl from "maplibre-gl";
import type { Map as MaplibreMap } from "maplibre-gl";

import Handles from "./Handles";
import { boundsToRect, rectToBounds, recalc } from "./calc";
import type { Bounds, Rect } from "./calc";

export interface ResizerProps {
  // Optional here because MapSelect renders `<Resizer .../>` without them - CanvasPlugin
  // injects both via `cloneElement` at runtime (see @xtramaps/web-map-maplibre-react's
  // CanvasOverlay), so they're only actually absent if Resizer is ever used outside that
  // wrapper, which the guard below turns into a clean no-render instead of a crash.
  map?: MaplibreMap;
  maplibre?: typeof maplibregl;
  bounds?: Bounds;
  onChange: (bounds: Bounds) => void;
}

const Resizer = ({
  map,
  maplibre,
  bounds = [
    [0, 0],
    [0, 0],
  ],
  onChange,
}: ResizerProps) => {
  const boxRef = useRef<HTMLDivElement>(null);
  const [rect, setRect] = useState<Rect>({ top: 0, left: 0, height: 0, width: 0 });

  // All hooks below have to run unconditionally (Rules of Hooks) even though `map`/`maplibre`
  // are only actually missing pre-mount - CanvasOverlay never renders this component at all
  // until the map is ready, so the `!` assertions reflect a real, stable invariant rather than
  // papering over a genuine null case.
  useEffect(() => {
    if (!map) return;
    const { top, left, bottom, right } = boundsToRect(map, bounds);

    setRect({ top, left, height: bottom - top, width: right - left });
  }, [map, bounds]);

  useEffect(() => {
    if (!map || !maplibre) return undefined;
    const update = () => onChange(rectToBounds(map, maplibre, rect));

    map.on("idle", update);

    return () => {
      map.off("idle", update);
    };
  }, [onChange, map, maplibre, rect]);

  useEffect(() => {
    const box = boxRef.current;
    if (!box) return;

    const { top, left, height, width } = rect;

    box.style.top = `${top}px`;
    box.style.left = `${left}px`;
    box.style.height = `${height}px`;
    box.style.width = `${width}px`;
    box.style.display = "block";
  }, [rect]);

  const onResize = (direction: Parameters<typeof recalc>[1], movement: Parameters<typeof recalc>[2]) => {
    const box = boxRef.current;
    if (!box || !map || !maplibre) return;

    const next = recalc(rect, direction, movement);

    onChange(rectToBounds(map, maplibre, next));
    setRect(next);
  };

  if (!map || !maplibre) {
    return null;
  }

  return (
    <div className="boxdraw" ref={boxRef}>
      <Handles map={map} onResize={onResize} />
    </div>
  );
};

Resizer.displayName = "Resizer";

export default Resizer;
