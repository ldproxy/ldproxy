import { Direction } from "./constants";

export const boundsToRect = (map, bounds) => {
  const sw = map.project(bounds[0]);
  const ne = map.project(bounds[1]);

  return { top: ne.y, left: sw.x, bottom: sw.y, right: ne.x };
};

export const rectToBounds = (map, maplibre, rect) => {
  const { top, left, height, width } = rect;

  const sw2 = map.unproject(new maplibre.Point(left, top + height));
  const ne2 = map.unproject(new maplibre.Point(left + width, top));

  return [
    [sw2.lng, sw2.lat],
    [ne2.lng, ne2.lat],
  ];
};

const minSize = 20;

export const recalc = (previous, direction, movement) => {
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
