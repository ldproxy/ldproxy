import proj4 from "proj4";

export const DEFAULT_CRS_URI = "http://www.opengis.net/def/crs/OGC/1.3/CRS84";

const CRS84_ALIASES = [
  "CRS84",
  "OGC:CRS84",
  "http://www.opengis.net/def/crs/OGC/0/CRS84",
  "http://www.opengis.net/def/crs/OGC/1.3/CRS84",
];

let projectionsInitialized = false;

const ensureProjections = () => {
  if (projectionsInitialized) {
    return;
  }

  proj4.defs("OGC:CRS84", "+proj=longlat +datum=WGS84 +no_defs +axis=enu");
  proj4.defs("EPSG:4258", "+proj=longlat +ellps=GRS80 +towgs84=0,0,0,0,0,0,0 +no_defs +axis=enu");
  proj4.defs(
    "EPSG:25832",
    "+proj=utm +zone=32 +ellps=GRS80 +towgs84=0,0,0,0,0,0,0 +units=m +no_defs",
  );
  proj4.defs(
    "EPSG:25833",
    "+proj=utm +zone=33 +ellps=GRS80 +towgs84=0,0,0,0,0,0,0 +units=m +no_defs",
  );
  proj4.defs("EPSG:3395", "+proj=merc +lon_0=0 +k=1 +x_0=0 +y_0=0 +datum=WGS84 +units=m +no_defs");

  projectionsInitialized = true;
};

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

const getProjCodeForCrs = (crsUri) => {
  const normalized = normalizeCrs(crsUri);
  if (normalized === DEFAULT_CRS_URI) {
    return "OGC:CRS84";
  }

  const epsgCode = normalized.match(/^EPSG:(\d+)$/i);
  if (epsgCode) {
    return `EPSG:${epsgCode[1]}`;
  }

  const epsgUri = normalized.match(/\/def\/crs\/EPSG\/(?:0|[0-9.]+)\/(\d+)$/);
  if (epsgUri) {
    return `EPSG:${epsgUri[1]}`;
  }

  const epsgUrn = normalized.match(/urn:ogc:def:crs:EPSG::(\d+)$/i);
  if (epsgUrn) {
    return `EPSG:${epsgUrn[1]}`;
  }

  return normalized;
};

const parseBbox = (bboxValue) => {
  const values = bboxValue.split(",").map((value) => parseFloat(value));
  if (values.length !== 4 || values.some((value) => Number.isNaN(value))) {
    throw new Error("Invalid bbox value");
  }
  return values;
};

const formatBbox = ([minX, minY, maxX, maxY]) =>
  [minX, minY, maxX, maxY].map((value) => Number(value).toFixed(4)).join(",");

export const transformBbox = (bboxValue, sourceCrs, targetCrs) => {
  ensureProjections();

  const src = getProjCodeForCrs(sourceCrs);
  const dst = getProjCodeForCrs(targetCrs);

  if (src === dst) {
    return bboxValue;
  }

  const [minX, minY, maxX, maxY] = parseBbox(bboxValue);
  const lowerLeft = proj4(src, dst, [minX, minY]);
  const upperRight = proj4(src, dst, [maxX, maxY]);

  return formatBbox([lowerLeft[0], lowerLeft[1], upperRight[0], upperRight[1]]);
};
