import Circle from "./Circle";
import Fill from "./Fill";
import Line from "./Line";
import Symbol from "./Symbol";
import { exprHandler } from "./util";

function extractPartOfImage(img, { x, y, width, height, pixelRatio }) {
  const dpi = 1 / pixelRatio;
  const el = document.createElement("canvas");
  el.width = width * dpi;
  el.height = height * dpi;
  const ctx = el.getContext("2d");
  ctx.drawImage(img, x, y, width, height, 0, 0, width * dpi, height * dpi);
  return { url: el.toDataURL(), dimensions: { width: width * dpi, height: height * dpi } };
}

export default function LegendSymbol({ sprite, zoom, layer, properties }) {
  const TYPE_MAP = {
    circle: Circle,
    symbol: Symbol,
    line: Line,
    fill: Fill,
  };

  const handler = TYPE_MAP[layer.type];
  const expr = exprHandler({ zoom, properties });
  const image = (imgKey) => {
    if (!imgKey) return {};
    const cleanKey = imgKey.includes(":") ? imgKey.split(":")[1] : imgKey;

    if (sprite && sprite.json) {
      const dimensions = sprite.json[cleanKey];
      const multipleSprites = sprite.sprites && Array.isArray(sprite.sprites);

      if (dimensions) {
        if (multipleSprites) {
          const individualSprite = sprite.sprites.find(
            (s) =>
              s.json.status === "fulfilled" &&
              s.json.value[cleanKey] &&
              s.image.status === "fulfilled"
          );
          if (individualSprite) {
            return extractPartOfImage(individualSprite.image.value, dimensions);
          }
        }
        return extractPartOfImage(sprite.image, dimensions);
      }
    }
    return {};
  };

  if (handler) {
    return handler({ layer, expr, image });
  }
  return null;
}
