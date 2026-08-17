import moment from "moment";
import type { Code } from "./types";

interface PropertySchema {
  "x-ogc-role"?: string;
  title?: string;
  enum?: string[];
  type?: string;
}

export interface CollectionLike {
  properties?: Record<string, PropertySchema>;
  extent?: {
    temporal?: { interval: [[string, string | null]] };
    spatial?: { bbox: number[][] };
  };
  crs?: string[];
}

export const getBaseUrl = (): URL => {
  let baseUrl = new URL(window.location.href);
  if (process.env.NODE_ENV !== "production") {
    baseUrl = new URL(
      "https://demo.ldproxy.net/cshapes/collections/boundary/items?limit=10&offset=10",
    );
  }
  return baseUrl;
};

export interface ExtractedFields {
  fields: Record<string, string>;
  code: Code;
  integerKeys: string[];
  booleanProperty: string[];
}

export const extractFields = (obj: CollectionLike | undefined): ExtractedFields => {
  const fields: Record<string, string> = {};
  const code: Code = {};
  const integerKeys: string[] = [];
  const booleanProperty: string[] = [];
  if (obj && obj.properties) {
    // eslint-disable-next-line
    for (const key in obj.properties) {
      if (obj.properties[key]["x-ogc-role"] && obj.properties[key]["x-ogc-role"]!.startsWith("primary-")) {
        // eslint-disable-next-line no-continue
        continue;
      }
      if (obj.properties[key].title) {
        fields[key] = obj.properties[key].title!;
      }
      if (obj.properties[key].enum) {
        code[key] = obj.properties[key].enum;
      }
      if (obj.properties[key].type === "integer") {
        integerKeys.push(key);
      }
      if (obj.properties[key].type === "boolean") {
        booleanProperty.push(key);
      }
    }
  }

  return { fields, code, integerKeys, booleanProperty };
};

export interface ExtractedInterval {
  start: number | null;
  end: number | null;
  temporal: { start?: number; end?: number };
}

export const extractInterval = (obj: CollectionLike | undefined): ExtractedInterval => {
  let start: number | null = null;
  let end: number | null = null;
  let temporal: { start?: number; end?: number } = {};

  const parseTemporalExtent = (temporalExtent: {
    interval: [[string, string | null]];
  }): { start: number; end: number } => {
    const starting = temporalExtent.interval[0][0];
    const ending = temporalExtent.interval[0][1];
    const startingUnix = moment.utc(starting).valueOf();
    const endingUnix = ending ? moment.utc(ending).valueOf() : moment.utc().valueOf();
    return { start: startingUnix, end: endingUnix };
  };

  if (obj && obj.extent) {
    const { temporal: temporalExtent } = obj.extent;

    if (temporalExtent) {
      temporal = parseTemporalExtent(temporalExtent);
    }

    start = temporal.start ?? null;
    end = temporal.end ?? null;
  }

  return { start, end, temporal };
};

export const extractSpatial = (obj: CollectionLike | undefined): { spatial: number[][] } => {
  let spatial: number[][] = [];

  if (obj && obj.extent) {
    const { spatial: spatialExtent } = obj.extent;

    if (spatialExtent) {
      const bounds = spatialExtent.bbox;
      const transformedBounds =
        bounds[0].length === 6
          ? bounds.map((innerArray) => [
              [innerArray[0], innerArray[1]],
              [innerArray[3], innerArray[4]],
            ])
          : bounds.map((innerArray) => [
              [innerArray[0], innerArray[1]],
              [innerArray[2], innerArray[3]],
            ]);
      spatial = transformedBounds.flat();
    }
  }
  return { spatial };
};
