export const emptyStyle = () => ({
  version: 8,
  sources: {},
  layers: [],
});

const normalizedUrl = (url = "") => {
  try {
    return decodeURIComponent(url);
  } catch {
    return url;
  }
};

const hasPlaceholder = (url, key) => new RegExp(`\\{${key}\\}`, "i").test(url);

export const isRasterTileUrl = (url = "") => {
  const decoded = normalizedUrl(url);
  return (
    hasPlaceholder(decoded, "z") && hasPlaceholder(decoded, "x") && hasPlaceholder(decoded, "y")
  );
};

const getBaseAttribution = (url, attribution, defaultUrl, defaultAttribution) =>
  url === defaultUrl && attribution !== defaultAttribution
    ? [attribution, defaultAttribution]
    : attribution || defaultAttribution;

const expandTileServers = (url) =>
  url.indexOf("{s}") > -1 || url.indexOf("{a-c}") > -1
    ? ["a", "b", "c"].map((prefix) => url.replace(/\{s\}/, prefix).replace(/\{a-c\}/, prefix))
    : [url];

export const rasterBaseStyle = (url, attribution, defaultUrl, defaultAttribution) => {
  const baseAttribution = getBaseAttribution(url, attribution, defaultUrl, defaultAttribution);

  const finalUrl = url || defaultUrl;
  const servers = expandTileServers(finalUrl);

  return {
    version: 8,
    sources: {
      base: {
        type: "raster",
        tiles: servers,
        tileSize: 256,
        attribution: baseAttribution,
      },
    },
    layers: [
      {
        id: "background",
        type: "raster",
        source: "base",
      },
    ],
  };
};

const isObject = (value) => typeof value === "object" && value !== null;

export const isValidStyle = (style) =>
  isObject(style) &&
  typeof style.version === "number" &&
  isObject(style.sources) &&
  Array.isArray(style.layers);

export const sanitizeBasemapStyle = (style) => {
  if (!isValidStyle(style)) {
    return null;
  }

  return {
    ...style,
    version: style.version || 8,
    sources: style.sources || {},
    layers: style.layers || [],
  };
};

export const fetchBasemapStyle = async (url) => {
  const response = await fetch(url);
  if (!response.ok) {
    throw new Error(`Failed to load basemap style: ${response.status}`);
  }
  const style = await response.json();
  const sanitizedStyle = sanitizeBasemapStyle(style);

  if (!sanitizedStyle) {
    throw new Error("Invalid basemap style");
  }

  return sanitizedStyle;
};

export const resolveWireframeBaseStyle = async ({
  backgroundUrl,
  attribution,
  defaultUrl,
  defaultAttribution,
}) => {
  const finalUrl = backgroundUrl || defaultUrl;

  if (isRasterTileUrl(finalUrl)) {
    return rasterBaseStyle(backgroundUrl, attribution, defaultUrl, defaultAttribution);
  }

  try {
    return await fetchBasemapStyle(finalUrl);
  } catch {
    return rasterBaseStyle(null, attribution, defaultUrl, defaultAttribution);
  }
};

// backward-compatible export name
export const baseStyle = rasterBaseStyle;

export const hoverLayers = ["points", "lines", "polygons"];

export const isDataLayer = (layer) => {
  switch (layer.type) {
    case "raster":
    case "hillshade":
    case "background":
      return false;
    default:
      return true;
  }
};

const circleLayers = (color, opacity, circleRadius, minZoom, maxZoom) => [
  {
    id: "points",
    type: "circle",
    source: "data",
    paint: {
      "circle-color": color,
      "circle-opacity": opacity,
      "circle-radius": circleRadius,
    },
    minzoom: minZoom,
    maxzoom: maxZoom,
  },
];

const lineLayers = (color, opacity, lineWidth, minZoom, maxZoom) => [
  {
    id: "lines",
    type: "line",
    source: "data",
    layout: {
      "line-join": "round",
      "line-cap": "round",
    },
    paint: {
      "line-color": color,
      "line-opacity": opacity,
      "line-width": lineWidth,
    },
    minzoom: minZoom,
    maxzoom: maxZoom,
  },
];

const polygonLayers = (color, opacity, fillOpacity, outlineWidth, minZoom, maxZoom) => [
  {
    id: "polygons",
    type: "fill",
    source: "data",
    paint: {
      "fill-color": color,
      "fill-opacity": fillOpacity,
    },
    minzoom: minZoom,
    maxzoom: maxZoom,
  },
  {
    id: "polygons-outline",
    type: "line",
    source: "data",
    layout: {
      "line-join": "round",
      "line-cap": "round",
    },
    paint: {
      "line-color": color,
      "line-opacity": opacity,
      "line-width": outlineWidth,
    },
    minzoom: minZoom,
    maxzoom: maxZoom,
  },
];

const withFilter = (layers, geometryType) =>
  layers.map((layer) => ({
    ...layer,
    filter: ["==", "$type", geometryType],
  }));

export const geoJsonLayers = ({
  color,
  opacity,
  circleRadius,
  circleMinZoom,
  circleMaxZoom,
  lineWidth,
  lineMinZoom,
  lineMaxZoom,
  fillOpacity,
  outlineWidth,
  polygonMinZoom,
  polygonMaxZoom,
}) => {
  return ["Polygon", "LineString", "Point"].flatMap((geometryType) => {
    switch (geometryType) {
      case "Point":
        return withFilter(
          circleLayers(color, opacity, circleRadius, circleMinZoom, circleMaxZoom),
          geometryType,
        );
      case "LineString":
        return withFilter(
          lineLayers(color, opacity, lineWidth, lineMinZoom, lineMaxZoom),
          geometryType,
        );
      case "Polygon":
        return withFilter(
          polygonLayers(color, opacity, fillOpacity, outlineWidth, polygonMinZoom, polygonMaxZoom),
          geometryType,
        );
      default:
        return [];
    }
  });
};

const withSourceAndFilter = (layers, source, geometryType) =>
  layers.map((layer) => ({
    ...layer,
    id: `${source}_${layer.id}`,
    "source-layer": source,
    filter: ["==", "$type", geometryType],
  }));

export const vectorLayers = (
  source,
  geometryTypes,
  {
    color,
    opacity,
    circleRadius,
    circleMinZoom,
    circleMaxZoom,
    lineWidth,
    lineMinZoom,
    lineMaxZoom,
    fillOpacity,
    outlineWidth,
    polygonMinZoom,
    polygonMaxZoom,
  },
) =>
  geometryTypes.flatMap((geometryType) => {
    switch (geometryType) {
      case "points":
        return withSourceAndFilter(
          circleLayers(color, opacity, circleRadius, circleMinZoom, circleMaxZoom),
          source,
          "Point",
        );
      case "lines":
        return withSourceAndFilter(
          lineLayers(color, opacity, lineWidth, lineMinZoom, lineMaxZoom),
          source,
          "LineString",
        );
      case "polygons":
        return withSourceAndFilter(
          polygonLayers(color, opacity, fillOpacity, outlineWidth, polygonMinZoom, polygonMaxZoom),
          source,
          "Polygon",
        );
      default:
        return [];
    }
  });
