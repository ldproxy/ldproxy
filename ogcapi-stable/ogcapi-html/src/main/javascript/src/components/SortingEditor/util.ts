import type { Code } from "./types";

interface PropertySchema {
  "x-ogc-role"?: string;
  title?: string;
  enum?: string[];
  type?: string;
}

export interface CollectionLike {
  properties?: Record<string, PropertySchema>;
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
      if (
        obj.properties[key]["x-ogc-role"] &&
        obj.properties[key]["x-ogc-role"]!.startsWith("primary-")
      ) {
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
