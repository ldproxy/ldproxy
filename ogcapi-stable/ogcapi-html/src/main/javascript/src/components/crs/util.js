export const CRS84_URI = "http://www.opengis.net/def/crs/OGC/1.3/CRS84";
export const CRS84H_URI = "http://www.opengis.net/def/crs/OGC/0/CRS84h";

// Backwards-compatible alias. In query handling this still represents CRS84.
export const DEFAULT_CRS_URI = CRS84_URI;

const CRS84_ALIASES = [
  "CRS84",
  "OGC:CRS84",
  "http://www.opengis.net/def/crs/OGC/0/CRS84",
  "http://www.opengis.net/def/crs/OGC/1.3/CRS84",
];

const CRS84H_ALIASES = ["CRS84h", "OGC:CRS84h", "http://www.opengis.net/def/crs/OGC/0/CRS84h"];

export const normalizeCrs = (crs, fallback = DEFAULT_CRS_URI) => {
  if (!crs) {
    return fallback;
  }

  if (
    CRS84_ALIASES.includes(crs) ||
    crs.endsWith("/OGC/0/CRS84") ||
    crs.endsWith("/OGC/1.3/CRS84")
  ) {
    return CRS84_URI;
  }

  if (CRS84H_ALIASES.includes(crs) || crs.endsWith("/OGC/0/CRS84h")) {
    return CRS84H_URI;
  }

  return crs;
};

export const toCanonicalCrsUri = (crs) => {
  const normalized = normalizeCrs(crs);
  if (normalized === CRS84_URI || normalized === CRS84H_URI) {
    return normalized;
  }

  const epsgCode = normalized.match(/^EPSG:(\d+)$/i);
  if (epsgCode) {
    return `http://www.opengis.net/def/crs/EPSG/0/${epsgCode[1]}`;
  }

  const epsgUrn = normalized.match(/urn:ogc:def:crs:EPSG::(\d+)$/i);
  if (epsgUrn) {
    return `http://www.opengis.net/def/crs/EPSG/0/${epsgUrn[1]}`;
  }

  const epsgUri = normalized.match(/\/def\/crs\/EPSG\/(?:0|[0-9.]+)\/(\d+)$/);
  if (epsgUri) {
    return `http://www.opengis.net/def/crs/EPSG/0/${epsgUri[1]}`;
  }

  return normalized;
};

export const getDefaultCollectionCrs = (crsValues) => {
  const firstCrs = (crsValues || []).find((crs) => crs && !crs.startsWith("#"));
  return normalizeCrs(firstCrs, DEFAULT_CRS_URI);
};
