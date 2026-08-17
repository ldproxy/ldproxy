import React, { useEffect, useMemo, useState, useRef } from "react";
import qs from "qs";
import { useTranslation } from "react-i18next";
import i18n from "../../i18n";
import Editor from "./Editor";
import EditorHeader from "./Editor/Header";
import { getBaseUrl, extractFields } from "./util";
import type { CollectionLike } from "./util";
import { useApiInfo } from "./hooks";
import type { Filters } from "./types";

const baseUrl = getBaseUrl();

const query = qs.parse(window.location.search, {
  ignoreQueryPrefix: true,
});

export interface SortingEditorProps {
  backgroundUrl?: string;
  attribution?: string;
}

const SortingEditor = ({
  backgroundUrl = "https://{a-c}.tile.openstreetmap.org/{z}/{x}/{y}.png",
  attribution = '&copy; <a href="http://osm.org/copyright">OpenStreetMap</a> contributors',
}: SortingEditorProps) => {
  const initialFilters = useRef<Filters>({});

  const [isOpen, setOpen] = useState(false);

  const [filters, setFilters] = useState<Filters>({});

  useEffect(() => {
    if (isOpen) {
      initialFilters.current = JSON.parse(JSON.stringify(filters));
    }
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [isOpen]);

  const urlProperties = new URL(
    baseUrl.pathname.endsWith("/") ? "../sortables" : "./sortables",
    baseUrl.href,
  );
  urlProperties.search = "?f=json";
  const {
    obj: properties,
    isLoaded: loadedProperties,
    error: errorProperties,
  } = useApiInfo<CollectionLike>(urlProperties);

  const { fields } = useMemo(() => extractFields(properties ?? undefined), [properties]);

  const enabled = loadedProperties;

  const { t } = useTranslation();

  // eslint-disable-next-line no-underscore-dangle
  const { language, translations } = globalThis._sortingfilter!;
  useEffect(() => {
    Object.entries(translations).forEach(([key, value]) => {
      i18n.addResourceBundle(language, "translation", { [key]: value }, true, true);
    });
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, []);

  const onAdd = (field: string, value: string) => {
    setFilters((prev) => ({
      ...prev,
      [field]: { value, add: true, remove: false },
    }));
  };

  useEffect(() => {
    setFilters(
      Object.keys(fields).reduce((reduced, field) => {
        if (query.sortby) {
          String(query.sortby)
            .split(",")
            .forEach((sortStr) => {
              const direction = sortStr.startsWith("+") ? "ascending" : "descending";
              const key = sortStr.slice(1);
              if (key === field) {
                reduced[field] = {
                  value: direction,
                  add: false,
                  remove: false,
                };
              }
            });
        }
        return reduced;
      }, {} as Filters),
    );
  }, [fields]);

  const save = (event: React.SyntheticEvent) => {
    (event.target as HTMLButtonElement).blur();
    delete query.offset;

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

    const sortFields = Object.keys(newFilters)
      .filter((key) => newFilters[key].value)
      .map((key) => {
        const direction = newFilters[key].value === "ascending" ? "+" : "-";
        return direction + key;
      });

    if (sortFields.length > 0) {
      query.sortby = sortFields.join(",");
    } else {
      delete query.sortby;
    }

    const url = qs.stringify(query, { addQueryPrefix: true });
    window.location.search = url;
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

  const hasFields = fields && Object.keys(fields).length > 0;

  const editorHeaderProps = {
    isOpen,
    setOpen,
    filters,
    save,
    cancel,
    isEnabled: enabled,
  };

  if (errorProperties) {
    // Hide error if it's a 404 (caused by disabled building block "sorting")
    if ((errorProperties as { status?: number }).status === 404) {
      return null;
    }
    return <div>{t("error")}</div>;
  }

  if (enabled && hasFields) {
    return (
      <>
        <EditorHeader {...editorHeaderProps} />
        <Editor
          isOpen={isOpen}
          fields={fields}
          filters={filters}
          onAdd={onAdd}
          deleteFilters={deleteFilters}
          titleForFilter={fields}
        />
      </>
    );
  }
  return null;
};

SortingEditor.displayName = "FilterEditor";

export default SortingEditor;
