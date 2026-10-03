import type * as maplibregl from "maplibre-gl";
import type { Map as MaplibreMap } from "maplibre-gl";
import { Direction } from "./constants";

export interface Rect {
  top: number;
  left: number;
  height: number;
  width: number;
}

export type Bounds = [[number, number], [number, number]];

export interface Movement {
  x: number;
  y: number;
}

export const boundsToRect = (
  map: MaplibreMap,
  bounds: Bounds,
): { top: number; left: number; bottom: number; right: number } => {
  const sw = map.project(bounds[0]);
  const ne = map.project(bounds[1]);

  return { top: ne.y, left: sw.x, bottom: sw.y, right: ne.x };
};

export const rectToBounds = (
  map: MaplibreMap,
  maplibre: typeof maplibregl,
  rect: Rect,
): Bounds => {
  const { top, left, height, width } = rect;

  const sw2 = map.unproject(new maplibre.Point(left, top + height));
  const ne2 = map.unproject(new maplibre.Point(left + width, top));

  return [
    [sw2.lng, sw2.lat],
    [ne2.lng, ne2.lat],
  ];
};

const minSize = 20;

export const recalc = (previous: Rect, direction: Direction, movement: Movement): Rect => {
  const { top, left, height, width } = previous;
  const { x, y } = movement;

  switch (direction) {
    case Direction.TopLeft:
      if (height - y < minSize) break;
      if (width - x < minSize) break;

      return {
        top: top + y,
        left: left + x,
        height: height - y,
        width: width - x,
      };
    case Direction.TopRight:
      if (height - y < minSize) break;
      if (width + x < minSize) break;

      return { top: top + y, left, height: height - y, width: width + x };
    case Direction.BottomLeft:
      if (height + y < minSize) break;
      if (width - x < minSize) break;

      return { top, left: left + x, height: height + y, width: width - x };
    case Direction.BottomRight:
      if (height + y < minSize) break;
      if (width + x < minSize) break;

      return { top, left, height: height + y, width: width + x };
    default:
      break;
  }

  return previous;
};
