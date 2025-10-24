import React, { useEffect, useMemo, useState } from "react";
import PropTypes from "prop-types";

import qs from "qs";

import Editor from "./Editor";
import EditorHeader from "./Editor/Header";
import { getBaseUrl, extractFields, extractInterval, extractSpatial } from "./util";
import { useApiInfo } from "./hooks";

const baseUrl = getBaseUrl();

// eslint-disable-next-line no-undef
const query = qs.parse(window.location.search, {
  ignoreQueryPrefix: true,
});

const toBounds = (filter) => {
  const a = filter.split(",");
  const b = [
    [parseFloat(a[0]), parseFloat(a[1])],
    [parseFloat(a[2]), parseFloat(a[3])],
  ];
  return b;
};

const FilterEditor = ({ backgroundUrl, attribution }) => {
  const urlSpatialTemporal = new URL(baseUrl.pathname.endsWith("/") ? "../" : "./", baseUrl.href);
  urlSpatialTemporal.search = "?f=json";

  const {
    obj: spatialTemporal,
    isLoaded: loadedSpatialTemporal,
    error: errorSpatialTemporal,
  } = useApiInfo(urlSpatialTemporal);

  const { start, end, temporal } = useMemo(
    () => extractInterval(spatialTemporal),
    [spatialTemporal]
  );
  const { spatial } = useMemo(() => extractSpatial(spatialTemporal), [spatialTemporal]);

  const urlProperties = new URL(
    baseUrl.pathname.endsWith("/") ? "../queryables" : "./queryables",
    baseUrl.href
  );
  urlProperties.search = "?f=json";

  const {
    obj: properties,
    isLoaded: loadedProperties,
    error: errorProperties,
  } = useApiInfo(urlProperties);

  const { fields, code, integerKeys, booleanProperty } = useMemo(
    () => extractFields(properties),
    [properties]
  );

  const [isOpen, setOpen] = useState(false);

  const enabled =
    loadedProperties &&
    loadedSpatialTemporal &&
    (Object.keys(fields).length > 0 || spatial || temporal);

  const [filters, setFilters] = useState({});

  useEffect(() => {
    setFilters(
      Object.keys(fields)
        .concat(["bbox", "datetime"])
        .reduce((reduced, field) => {
          if (query[field]) {
            // eslint-disable-next-line no-param-reassign
            reduced[field] = {
              value: query[field],
              add: false,
              remove: false,
            };
          }
          return reduced;
        }, {})
    );
  }, [fields]);

  const onAdd = (field, value) => {
    setFilters((prev) => ({
      ...prev,
      [field]: { value, add: true, remove: false },
    }));
  };

  const onRemove = (field) => {
    setFilters((prev) => ({
      ...prev,
      [field]: { value: prev[field].value, add: false, remove: true },
    }));
  };

  const save = (event) => {
    event.target.blur();

    const newFilters = Object.keys(filters).reduce((reduced, key) => {
      if (filters[key].add || !filters[key].remove) {
        // eslint-disable-next-line no-param-reassign
        reduced[key] = {
          ...filters[key],
          add: false,
          remove: false,
        };
      }
      return reduced;
    }, {});

    delete query.offset;

    Object.keys(fields)
      .concat(["bbox", "datetime"])
      .forEach((field) => {
        delete query[field];
        if (newFilters[field]) {
          query[field] = newFilters[field].value;
        }
      });

    // eslint-disable-next-line no-undef
    window.location.search = qs.stringify(query, {
      addQueryPrefix: true,
    });
  };

  const deleteFilters = (field) => () => {
    setFilters((current) => {
      const copy = { ...current };
      delete copy[field];
      return copy;
    });
  };

  const cancel = (event) => {
    event.target.blur();

    const newFilters = Object.keys(filters).reduce((reduced, key) => {
      if (!filters[key].add) {
        // eslint-disable-next-line no-param-reassign
        reduced[key] = {
          ...filters[key],
          add: false,
          remove: false,
        };
      }
      return reduced;
    }, {});

    setFilters(newFilters);
    setOpen(false);
  };

  const editorHeaderProps = {
    isOpen,
    setOpen,
    isEnabled: enabled,
    filters,
    save,
    cancel,
    onRemove,
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
          start={start}
          end={end}
          setFilters={setFilters}
          integerKeys={integerKeys}
          booleanProperty={booleanProperty}
        />
      ) : (
        <>
          {errorSpatialTemporal && <div>Error loading spatial-temporal data</div>}
          {errorProperties && <div>Error loading properties data</div>}
        </>
      )}
    </>
  );
};

FilterEditor.displayName = "FilterEditor";

FilterEditor.propTypes = {
  backgroundUrl: PropTypes.string,
  attribution: PropTypes.string,
};

FilterEditor.defaultProps = {
  backgroundUrl: "https://{a-c}.tile.openstreetmap.org/{z}/{x}/{y}.png",
  attribution: '&copy; <a href="http://osm.org/copyright">OpenStreetMap</a> contributors',
};

export default FilterEditor;
