import React, { useEffect, useMemo, useState } from "react";
import PropTypes from "prop-types";

import qs from "qs";

import Editor from "./Editor";
import EditorHeader from "./Editor/Header";
import { getBaseUrl, extractFields } from "./util";
import { useApiInfo } from "./hooks";

const baseUrl = getBaseUrl();

// eslint-disable-next-line no-undef
const query = qs.parse(window.location.search, {
  ignoreQueryPrefix: true,
});

const FilterEditor = ({ backgroundUrl, attribution }) => {
  const urlProperties = new URL(
    baseUrl.pathname.endsWith("/") ? "../sortables" : "./sortables",
    baseUrl.href
  );
  urlProperties.search = "?f=json";

  const {
    obj: properties,
    isLoaded: loadedProperties,
    error: errorProperties,
  } = useApiInfo(urlProperties);

  const { fields } = useMemo(() => extractFields(properties), [properties]);

  const [isOpen, setOpen] = useState(false);

  const enabled = loadedProperties;

  const [filters, setFilters] = useState({});

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

  useEffect(() => {
    setFilters(
      Object.keys(fields).reduce((reduced, field) => {
        if (query.sortby) {
          query.sortby.split(",").forEach((sortStr) => {
            const direction = sortStr.startsWith("+") ? "ascending" : "descending";
            const key = sortStr.slice(1);
            if (key === field) {
              // eslint-disable-next-line no-param-reassign
              reduced[field] = {
                value: direction,
                add: false,
                remove: false,
              };
            }
          });
        }
        return reduced;
      }, {})
    );
  }, [fields]);

  const save = (event) => {
    event.target.blur();
    delete query.offset;

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

  const hasFields = fields && Object.keys(fields).length > 0;

  const editorHeaderProps = {
    isOpen,
    setOpen,
    isEnabled: enabled,
    filters,
    save,
    cancel,
    onRemove,
  };

  if (errorProperties) {
    return <div>Error loading properties data</div>;
  }

  if (enabled && hasFields) {
    return (
      <>
        <EditorHeader {...editorHeaderProps} />
        <Editor
          isOpen={isOpen}
          fields={fields}
          backgroundUrl={backgroundUrl}
          attribution={attribution}
          filters={filters}
          onAdd={onAdd}
          deleteFilters={deleteFilters}
          titleForFilter={fields}
          setFilters={setFilters}
        />
      </>
    );
  }
  return null;
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
