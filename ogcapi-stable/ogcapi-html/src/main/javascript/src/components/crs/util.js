export const DEFAULT_CRS_URI = "http://www.opengis.net/def/crs/OGC/1.3/CRS84";

const CRS84_ALIASES = [
  "CRS84",
  "OGC:CRS84",
  "http://www.opengis.net/def/crs/OGC/0/CRS84",
  "http://www.opengis.net/def/crs/OGC/1.3/CRS84",
];

export const normalizeCrs = (crs) => {
  if (!crs) {
    return DEFAULT_CRS_URI;
  }

  if (
    CRS84_ALIASES.includes(crs) ||
    crs.endsWith("/OGC/0/CRS84") ||
    crs.endsWith("/OGC/1.3/CRS84")
  ) {
    return DEFAULT_CRS_URI;
  }

  return crs;
};

export const toCanonicalCrsUri = (crs) => {
  const normalized = normalizeCrs(crs);
  if (normalized === DEFAULT_CRS_URI) {
    return DEFAULT_CRS_URI;
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
