import React, { useState, useEffect, useMemo, useCallback } from "react";
import PropTypes from "prop-types";

import {
  Button,
  ButtonGroup,
  Form,
  FormGroup,
  Input,
  Row,
  Col,
  FormFeedback,
  FormText,
} from "reactstrap";

import {
  areBoundsValid,
  boundsAsArray,
  boundsAsObject,
  boundsAsString,
  boundsObjectEqualsArray,
  round,
  validateBounds,
} from "./util";
import { useDebounce } from "../../hooks";

export { default as MapSelect } from "./MapSelect";
export { round, roundBounds, boundsArraysEqual } from "./util";

const SpatialFilter = ({ bounds, setBounds, onChange, filters, deleteFilters }) => {
  const [inputs, setInputs] = useState(boundsAsObject(bounds));
  const debouncedInput = useDebounce(inputs, 1000);

  useEffect(() => {
    setInputs((prev) => {
      if (boundsObjectEqualsArray(prev, bounds)) {
        return prev;
      }
      return boundsAsObject(bounds);
    });
  }, [bounds]);

  useEffect(() => {
    if (areBoundsValid(debouncedInput)) {
      const newBounds = boundsAsArray(debouncedInput);
      setBounds(newBounds, true);
    }
  }, [setBounds, debouncedInput]);

  const valid = useMemo(() => validateBounds(inputs), [inputs]);

  const hasBboxInFilters = Object.keys(filters).some(
    (key) => filters[key].remove === false && key === "bbox" && key !== "datetime"
  );

  const onInputChange = useCallback((event) => {
    const { name, value } = event.target;
    const newValue = parseFloat(value);
    if (!Number.isNaN(newValue)) {
      setInputs((prev) => (newValue === prev[name] ? prev : { ...prev, [name]: round(newValue) }));
    }
  }, []);

  const save = () => onChange("bbox", boundsAsString(inputs));

  const onInputKey = (event) => {
    if (event.key === "Enter" && valid.all) {
      event.preventDefault();
      event.stopPropagation();
      save();
    }
  };

  return (
    <Form onSubmit={save}>
      <p className="text-muted text-uppercase">bbox</p>
      <Row>
        <Col md="5">
          <FormGroup>
            <Input
              type="number"
              size="sm"
              name="minLng"
              id="minLng"
              className={valid.minMaxLng && valid.minLng ? "mr-2" : "mr-2 is-invalid"}
              value={inputs.minLng}
              onChange={onInputChange}
              onKeyPress={onInputKey}
            />
            {valid.minMaxLng && valid.minLng && <FormText>Min. Longitude</FormText>}
            {!valid.minMaxLng && <FormFeedback>Min. greater than Max.</FormFeedback>}
            {!valid.minLng && <FormFeedback>Value too low/high for Lng</FormFeedback>}
          </FormGroup>
        </Col>
        <Col md="5">
          <FormGroup>
            <Input
              type="number"
              size="sm"
              name="minLat"
              id="minLat"
              className={valid.minMaxLat && valid.minLat ? "mr-2" : "mr-2 is-invalid"}
              value={inputs.minLat}
              onChange={onInputChange}
              onKeyPress={onInputKey}
            />
            {valid.minMaxLat && valid.minLat && <FormText>Min. Latitude</FormText>}
            {!valid.minMaxLat && <FormFeedback>Min. greater than Max.</FormFeedback>}
            {!valid.minLat && <FormFeedback>Value too low/high for Lat</FormFeedback>}
          </FormGroup>
        </Col>
      </Row>
      <Row>
        <Col md="5">
          <FormGroup>
            <Input
              type="number"
              size="sm"
              name="maxLng"
              id="maxLng"
              className={valid.minMaxLng && valid.maxLng ? "mr-2" : "mr-2 is-invalid"}
              value={inputs.maxLng}
              onChange={onInputChange}
              onKeyPress={onInputKey}
            />
            {valid.minMaxLng && valid.maxLng && <FormText>Max. Longitude</FormText>}
            {!valid.minMaxLng && <FormFeedback>Min. greater than Max.</FormFeedback>}
            {!valid.maxLng && <FormFeedback>Value too low/high for Lng</FormFeedback>}
          </FormGroup>
        </Col>
        <Col md="5">
          <FormGroup>
            <Input
              type="number"
              size="sm"
              name="maxLat"
              id="maxLat"
              className={valid.minMaxLat && valid.maxLat ? "mr-2" : "mr-2 is-invalid"}
              value={inputs.maxLat}
              onChange={onInputChange}
              onKeyPress={onInputKey}
            />
            {valid.minMaxLat && valid.maxLat && <FormText>Max. Latitude</FormText>}
            {!valid.minMaxLat && <FormFeedback>Min. greater than Max.</FormFeedback>}
            {!valid.maxLat && <FormFeedback>Value too low/high for Lat</FormFeedback>}
          </FormGroup>
        </Col>
        {hasBboxInFilters ? (
          <Col md="2">
            <ButtonGroup>
              <Button
                color="primary"
                size="sm"
                style={{ minWidth: "40px" }}
                disabled={!valid.all}
                onClick={save}
              >
                {"\u2713"}
              </Button>
              <Button
                color="danger"
                size="sm"
                style={{ minWidth: "40px" }}
                onClick={deleteFilters("bbox")}
              >
                {"\u2716"}
              </Button>
            </ButtonGroup>
          </Col>
        ) : (
          <Col md="2">
            <Button color="primary" size="sm" disabled={!valid.all} onClick={save}>
              Add
            </Button>
          </Col>
        )}
      </Row>
    </Form>
  );
};

SpatialFilter.displayName = "SpatialFilter";

SpatialFilter.propTypes = {
  bounds: PropTypes.arrayOf(PropTypes.arrayOf(PropTypes.string)),
  onChange: PropTypes.func.isRequired,
  // eslint-disable-next-line react/forbid-prop-types
  filters: PropTypes.object.isRequired,
  deleteFilters: PropTypes.func.isRequired,
  setBounds: PropTypes.func.isRequired,
};

SpatialFilter.defaultProps = {
  bounds: [
    [0, 0],
    [0, 0],
  ],
};

export default SpatialFilter;
