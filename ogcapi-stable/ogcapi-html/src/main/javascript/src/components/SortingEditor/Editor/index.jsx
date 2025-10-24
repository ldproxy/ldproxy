import React, { useCallback, useState } from "react";
import PropTypes from "prop-types";

import { Row, Col, Collapse } from "reactstrap";

import FieldFilter from "./Fields";
import TemporalFilter from "./Temporal";
import SpatialFilter, { MapSelect, roundBounds, boundsArraysEqual } from "./Spatial";

const EditorBody = ({
  isOpen,
  fields,
  backgroundUrl,
  attribution,
  spatial,
  filters,
  onAdd,
  deleteFilters,
  code,
  titleForFilter,
  start,
  end,
  temporal,
  integerKeys,
  booleanProperty,
}) => {
  const [showMap, setShowMap] = useState(false);
  const [bounds, setBounds] = useState(roundBounds(spatial));
  const [mapFlag, setMapFlag] = useState(true);

  const refreshMap = useCallback(() => setMapFlag((prev) => !prev), []);

  const setBoundsIfNecessary = useCallback(
    (newBounds, doRefreshMap) => {
      const rounded = roundBounds(newBounds);

      setBounds((prev) => {
        if (boundsArraysEqual(prev, rounded)) {
          return prev;
        }
        if (doRefreshMap) {
          refreshMap();
        }
        return rounded;
      });
    },
    [refreshMap]
  );

  return (
    <Collapse isOpen={isOpen} onEntered={() => setShowMap(true)}>
      <Row>
        <Col md="6">
          {Object.keys(fields).length > 0 && (
            <FieldFilter
              fields={Object.keys(fields)
                .filter((k) => !filters[k])
                .reduce(
                  (fs, k) => ({
                    ...fs,
                    [k]: fields[k],
                  }),
                  {}
                )}
              onAdd={onAdd}
              filters={filters}
              deleteFilters={deleteFilters}
              code={code}
              titleForFilter={titleForFilter}
              integerKeys={integerKeys}
              booleanProperty={booleanProperty}
            />
          )}
          {spatial && spatial.length > 0 && (
            <SpatialFilter
              bounds={bounds}
              setBounds={setBoundsIfNecessary}
              onChange={onAdd}
              filters={filters}
              deleteFilters={deleteFilters}
              refreshMap={refreshMap}
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
        <Col md="6">
          {showMap && spatial && spatial.length > 0 && (
            <MapSelect
              key={JSON.stringify(mapFlag)}
              backgroundUrl={backgroundUrl}
              attribution={attribution}
              bounds={bounds}
              onChange={setBoundsIfNecessary}
            />
          )}
        </Col>
      </Row>
    </Collapse>
  );
};

EditorBody.displayName = "EditorBody";

EditorBody.propTypes = {
  isOpen: PropTypes.bool,
  fields: PropTypes.objectOf(PropTypes.string),
  backgroundUrl: PropTypes.string.isRequired,
  attribution: PropTypes.string.isRequired,
  spatial: PropTypes.arrayOf(PropTypes.arrayOf(PropTypes.number)),
  // eslint-disable-next-line react/forbid-prop-types
  filters: PropTypes.object.isRequired,
  onAdd: PropTypes.func,
  deleteFilters: PropTypes.func.isRequired,
  // eslint-disable-next-line react/forbid-prop-types
  code: PropTypes.object.isRequired,
  titleForFilter: PropTypes.objectOf(PropTypes.string).isRequired,
  start: PropTypes.number.isRequired,
  end: PropTypes.number.isRequired,
  temporal: PropTypes.objectOf(PropTypes.number).isRequired,
  integerKeys: PropTypes.arrayOf(PropTypes.string).isRequired,
  booleanProperty: PropTypes.arrayOf(PropTypes.string).isRequired,
};

EditorBody.defaultProps = {
  isOpen: false,
  fields: {},
  spatial: null,
  onAdd: () => {},
};

export default EditorBody;
