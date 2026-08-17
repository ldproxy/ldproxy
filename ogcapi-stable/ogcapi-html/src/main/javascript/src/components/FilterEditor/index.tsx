import React, { useEffect, useMemo, useState, useRef } from "react";
import qs from "qs";
import { useTranslation } from "react-i18next";
import i18n from "../../i18n";
import Editor from "./Editor";
import EditorHeader from "./Editor/Header";
import { getBaseUrl, extractFields, extractInterval, extractSpatial } from "./util";
import type { CollectionLike } from "./util";
import { useApiInfo } from "./hooks";
import { CRS84_URI, getDefaultCollectionCrs, normalizeCrs } from "../crs/util";
import type { Filters } from "./types";
import type { BoundsArray } from "./Editor/Spatial/util";

const baseUrl = getBaseUrl();

const query = qs.parse(window.location.search, {
  ignoreQueryPrefix: true,
});

const toBounds = (filter: string): BoundsArray => {
  const a = filter.split(",");
  return [
    [parseFloat(a[0]), parseFloat(a[1])],
    [parseFloat(a[2]), parseFloat(a[3])],
  ];
};

export interface FilterEditorProps {
  backgroundUrl?: string;
  attribution?: string;
}

const FilterEditor = ({
  backgroundUrl = "https://{a-c}.tile.openstreetmap.org/{z}/{x}/{y}.png",
  attribution = '&copy; <a href="http://osm.org/copyright">OpenStreetMap</a> contributors',
}: FilterEditorProps) => {
  const initialFilters = useRef<Filters>({});

  const [isOpen, setOpen] = useState(false);

  const [filters, setFilters] = useState<Filters>({});

  useEffect(() => {
    if (isOpen) {
      initialFilters.current = JSON.parse(JSON.stringify(filters));
    }
  }, [filters, isOpen]);

  const urlSpatialTemporal = new URL(baseUrl.pathname.endsWith("/") ? "../" : "./", baseUrl.href);
  urlSpatialTemporal.search = "?f=json";

  const {
    obj: spatialTemporal,
    isLoaded: loadedSpatialTemporal,
    error: errorSpatialTemporal,
  } = useApiInfo<CollectionLike>(urlSpatialTemporal);

  const { start, end, temporal } = useMemo(
    () => extractInterval(spatialTemporal ?? undefined),
    [spatialTemporal],
  );
  const { spatial } = useMemo(() => extractSpatial(spatialTemporal ?? undefined), [spatialTemporal]);
  const defaultCollectionCrs = useMemo(
    () => getDefaultCollectionCrs(spatialTemporal?.crs),
    [spatialTemporal],
  );

  const { t } = useTranslation();

  // eslint-disable-next-line no-underscore-dangle
  const { language, translations } = globalThis._filter!;
  useEffect(() => {
    Object.entries(translations).forEach(([key, value]) => {
      i18n.addResourceBundle(language, "translation", { [key]: value }, true, true);
    });
  }, [language, translations]);

  const urlProperties = new URL(
    baseUrl.pathname.endsWith("/") ? "../queryables" : "./queryables",
    baseUrl.href,
  );
  urlProperties.search = "?f=json";

  const {
    obj: properties,
    isLoaded: loadedProperties,
    error: errorProperties,
  } = useApiInfo<CollectionLike>(urlProperties);

  const { fields, code, integerKeys, booleanProperty } = useMemo(
    () => extractFields(properties ?? undefined),
    [properties],
  );

  const enabled = Boolean(
    loadedProperties &&
      loadedSpatialTemporal &&
      (Object.keys(fields).length > 0 || spatial.length > 0 || Object.keys(temporal).length > 0),
  );

  useEffect(() => {
    setFilters(
      Object.keys(fields)
        .concat(["bbox", "datetime"])
        .reduce((reduced, field) => {
          const queryValue = query[field];
          if (queryValue) {
            reduced[field] = {
              value: String(queryValue),
              add: false,
              remove: false,
            };
          }
          return reduced;
        }, {} as Filters),
    );
  }, [fields]);

  const onAdd = (field: string, value: string) => {
    setFilters((prev) => ({
      ...prev,
      [field]: { value, add: true, remove: false },
    }));
  };

  const save = (event: React.SyntheticEvent) => {
    (event.target as HTMLButtonElement).blur();

    const newFilters = Object.keys(filters).reduce((reduced, key) => {
      if (filters[key].add || !filters[key].remove) {
        reduced[key] = {
          ...filters[key],
          add: false,
          remove: false,
        };
      }
      return reduced;
    }, {} as Filters);

    delete query.offset;
    delete query["bbox-crs"];

    Object.keys(fields)
      .concat(["datetime"])
      .forEach((field) => {
        delete query[field];
        if (newFilters[field]) {
          query[field] = newFilters[field].value;
        }
      });

    delete query.bbox;
    if (newFilters.bbox) {
      const selectedCrsUri = normalizeCrs(query.crs as string | undefined, defaultCollectionCrs);
      const hasExplicitCrs = Boolean(query.crs);
      query.bbox = newFilters.bbox.value;

      if (!hasExplicitCrs || selectedCrsUri === CRS84_URI) {
        delete query["bbox-crs"];
      } else {
        query["bbox-crs"] = CRS84_URI;
      }
    }

    window.location.search = qs.stringify(query, {
      addQueryPrefix: true,
    });
  };

  const deleteFilters = (field: string) => () => {
    setFilters((current) => {
      const copy = { ...current };
      copy[field].remove = true;
      return copy;
    });
  };

  const cancel = (event: React.SyntheticEvent) => {
    (event.target as HTMLButtonElement).blur();
    setFilters(initialFilters.current);
    setOpen(false);
  };

  const editorHeaderProps = {
    isOpen,
    setOpen,
    isEnabled: enabled,
    filters,
    save,
    cancel,
  };

  return (
    <>
      <EditorHeader {...editorHeaderProps} />
      {enabled ? (
        <Editor
          isOpen={isOpen}
          fields={fields}
          backgroundUrl={backgroundUrl}
          attribution={attribution}
          spatial={filters.bbox ? toBounds(filters.bbox.value) : spatial}
          temporal={temporal}
          filters={filters}
          onAdd={onAdd}
          deleteFilters={deleteFilters}
          code={code}
          titleForFilter={fields}
          start={start ?? 0}
          end={end ?? 0}
          integerKeys={integerKeys}
          booleanProperty={booleanProperty}
        />
      ) : (
        <>
          {errorSpatialTemporal ? <div>{t("error.spatialTemporal")}</div> : null}
          {errorProperties ? <div>{t("error")}</div> : null}
        </>
      )}
    </>
  );
};

FilterEditor.displayName = "FilterEditor";

export default FilterEditor;
