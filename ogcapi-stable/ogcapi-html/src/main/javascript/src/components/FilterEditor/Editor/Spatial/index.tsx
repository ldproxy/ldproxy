import React, { useState, useEffect, useMemo, useCallback } from "react";
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
import { useTranslation } from "react-i18next";
import type { Filters } from "../../types";

import {
  areBoundsValid,
  boundsAsArray,
  boundsAsObject,
  boundsAsString,
  boundsObjectEqualsArray,
  round,
  validateBounds,
} from "./util";
import type { BoundsArray, BoundsObject } from "./util";
import { useDebounce } from "../../hooks";

export { default as MapSelect } from "./MapSelect";
export { round, roundBounds, boundsArraysEqual } from "./util";

export interface SpatialFilterProps {
  bounds?: BoundsArray;
  setBounds: (bounds: BoundsArray, debounced: boolean) => void;
  onChange: (field: string, value: string) => void;
  filters: Filters;
  deleteFilters: (field: string) => () => void;
}

const SpatialFilter = ({
  bounds = [
    [0, 0],
    [0, 0],
  ],
  setBounds,
  onChange,
  filters,
  deleteFilters,
}: SpatialFilterProps) => {
  const [inputs, setInputs] = useState<BoundsObject>(boundsAsObject(bounds));
  const debouncedInput = useDebounce(inputs, 1000);
  const { t } = useTranslation();

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
    (key) => filters[key].remove === false && key === "bbox",
  );

  const onInputChange = useCallback((event: React.ChangeEvent<HTMLInputElement>) => {
    const { name, value } = event.target;
    const newValue = parseFloat(value);
    if (!Number.isNaN(newValue)) {
      setInputs((prev) => (
        newValue === prev[name as keyof BoundsObject]
          ? prev
          : { ...prev, [name]: round(newValue) }
      ));
    }
  }, []);

  const save = () => onChange("bbox", boundsAsString(inputs));

  const onInputKey = (event: React.KeyboardEvent<HTMLInputElement>) => {
    if (event.key === "Enter" && valid.all) {
      event.preventDefault();
      event.stopPropagation();
      save();
    }
  };

  return (
    <Form onSubmit={save}>
      <p className="text-muted text-uppercase">{t("bbox")}</p>
      <Row>
        <Col md="5">
          <FormGroup>
            <Input
              type="number"
              bsSize="sm"
              name="minLng"
              id="minLng"
              className={valid.minMaxLng && valid.minLng ? "mr-2" : "mr-2 is-invalid"}
              value={inputs.minLng}
              onChange={onInputChange}
              onKeyPress={onInputKey}
            />
            {valid.minMaxLng && valid.minLng && <FormText>{t("minLongitude")}</FormText>}
            {!valid.minMaxLng && <FormFeedback>{t("error.minGreaterThanMax")}</FormFeedback>}
            {!valid.minLng && <FormFeedback>{t("error.valueLng")}</FormFeedback>}
          </FormGroup>
        </Col>
        <Col md="5">
          <FormGroup>
            <Input
              type="number"
              bsSize="sm"
              name="minLat"
              id="minLat"
              className={valid.minMaxLat && valid.minLat ? "mr-2" : "mr-2 is-invalid"}
              value={inputs.minLat}
              onChange={onInputChange}
              onKeyPress={onInputKey}
            />
            {valid.minMaxLat && valid.minLat && <FormText>{t("minLatitude")}</FormText>}
            {!valid.minMaxLat && <FormFeedback>{t("error.minGreaterThanMax")}</FormFeedback>}
            {!valid.minLat && <FormFeedback>{t("error.valueLat")}</FormFeedback>}
          </FormGroup>
        </Col>
      </Row>
      <Row>
        <Col md="5">
          <FormGroup>
            <Input
              type="number"
              bsSize="sm"
              name="maxLng"
              id="maxLng"
              className={valid.minMaxLng && valid.maxLng ? "mr-2" : "mr-2 is-invalid"}
              value={inputs.maxLng}
              onChange={onInputChange}
              onKeyPress={onInputKey}
            />
            {valid.minMaxLng && valid.maxLng && <FormText>{t("maxLongitude")}</FormText>}
            {!valid.minMaxLng && <FormFeedback>{t("error.minGreaterThanMax")}</FormFeedback>}
            {!valid.maxLng && <FormFeedback>{t("error.valueLng")}</FormFeedback>}
          </FormGroup>
        </Col>
        <Col md="5">
          <FormGroup>
            <Input
              type="number"
              bsSize="sm"
              name="maxLat"
              id="maxLat"
              className={valid.minMaxLat && valid.maxLat ? "mr-2" : "mr-2 is-invalid"}
              value={inputs.maxLat}
              onChange={onInputChange}
              onKeyPress={onInputKey}
            />
            {valid.minMaxLat && valid.maxLat && <FormText>{t("maxLatitude")}</FormText>}
            {!valid.minMaxLat && <FormFeedback>{t("error.minGreaterThanMax")}</FormFeedback>}
            {!valid.maxLat && <FormFeedback>{t("error.valueLat")}</FormFeedback>}
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
                {"✓"}
              </Button>
              <Button
                color="danger"
                size="sm"
                style={{ minWidth: "40px" }}
                onClick={deleteFilters("bbox")}
              >
                {"✖"}
              </Button>
            </ButtonGroup>
          </Col>
        ) : (
          <Col md="2">
            <Button color="primary" size="sm" disabled={!valid.all} onClick={save}>
              {t("add")}
            </Button>
          </Col>
        )}
      </Row>
    </Form>
  );
};

SpatialFilter.displayName = "SpatialFilter";

export default SpatialFilter;
