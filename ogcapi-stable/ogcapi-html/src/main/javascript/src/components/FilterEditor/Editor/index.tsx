import React, { useCallback, useState } from "react";
import { Row, Col, Collapse } from "reactstrap";
import type { Code, Filters } from "../types";

import FieldFilter from "./Fields";
import TemporalFilter from "./Temporal";
import SpatialFilter, { MapSelect, roundBounds, boundsArraysEqual } from "./Spatial";
import type { BoundsArray } from "./Spatial/util";

export interface EditorBodyProps {
  isOpen?: boolean;
  fields?: Record<string, string>;
  backgroundUrl: string;
  attribution: string;
  spatial?: number[][] | null;
  filters: Filters;
  onAdd?: (field: string, value: string) => void;
  deleteFilters: (field: string) => () => void;
  code: Code;
  titleForFilter: Record<string, string>;
  start: number;
  end: number;
  temporal: Record<string, number>;
  integerKeys: string[];
  booleanProperty: string[];
}

const EditorBody = ({
  isOpen = false,
  fields = {},
  backgroundUrl,
  attribution,
  spatial = null,
  filters,
  onAdd = () => {},
  deleteFilters,
  code,
  titleForFilter,
  start,
  end,
  temporal,
  integerKeys,
  booleanProperty,
}: EditorBodyProps) => {
  const [showMap, setShowMap] = useState(false);
  const [bounds, setBounds] = useState<BoundsArray | []>(roundBounds(spatial));
  const [mapFlag, setMapFlag] = useState(true);

  const refreshMap = useCallback(() => setMapFlag((prev) => !prev), []);

  const setBoundsIfNecessary = useCallback(
    (newBounds: number[][], doRefreshMap?: boolean) => {
      const rounded = roundBounds(newBounds);

      setBounds((prev) => {
        if (boundsArraysEqual(prev as BoundsArray, rounded as BoundsArray)) {
          return prev;
        }
        if (doRefreshMap) {
          refreshMap();
        }
        return rounded;
      });
    },
    [refreshMap],
  );

  return (
    <Collapse isOpen={isOpen} onEntered={() => setShowMap(true)} className={isOpen ? "mb-4" : ""}>
      <Row>
        <Col md="7">
          {Object.keys(fields).length > 0 && (
            <FieldFilter
              fields={Object.keys(fields)
                .filter((k) => !filters[k])
                .reduce(
                  (fs, k) => ({
                    ...fs,
                    [k]: fields[k],
                  }),
                  {} as Record<string, string>,
                )}
              onAdd={onAdd}
              filters={filters}
              deleteFilters={deleteFilters}
              code={code}
              titleForFilter={titleForFilter}
              integerKeys={integerKeys}
              booleanProperty={booleanProperty}
              isOpen={isOpen}
            />
          )}
          {spatial && spatial.length > 0 && (
            <SpatialFilter
              bounds={bounds.length > 0 ? (bounds as BoundsArray) : undefined}
              setBounds={setBoundsIfNecessary}
              onChange={onAdd}
              filters={filters}
              deleteFilters={deleteFilters}
            />
          )}
          {temporal && Object.keys(temporal).length > 0 && (
            <TemporalFilter
              start={start}
              end={end}
              filter={filters.datetime ? filters.datetime.value : null}
              onChange={onAdd}
              filters={filters}
              deleteFilters={deleteFilters}
            />
          )}
        </Col>
        <Col md="5">
          {showMap && spatial && spatial.length > 0 && (
            <MapSelect
              key={JSON.stringify(mapFlag)}
              backgroundUrl={backgroundUrl}
              attribution={attribution}
              bounds={bounds.length > 0 ? (bounds as BoundsArray) : undefined}
              onChange={setBoundsIfNecessary}
            />
          )}
        </Col>
      </Row>
    </Collapse>
  );
};

EditorBody.displayName = "EditorBody";

export default EditorBody;
