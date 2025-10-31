import React, { useState, useEffect } from "react";
import PropTypes from "prop-types";
import "bootstrap/dist/css/bootstrap.min.css";
import { Button, ButtonGroup, Form, FormGroup, Input, Row, Col } from "reactstrap";
import { useTranslation } from "react-i18next";
import FilterValueField from "./FilterValueField";
import ValueField from "./ValueField";

const FieldFilter = ({ fields, onAdd, filters, deleteFilters, titleForFilter, isOpen }) => {
  const [field, setField] = useState("");
  const [value, setValue] = useState("ascending");
  const [changedValue, setChangedValue] = useState("");
  const { t } = useTranslation();

  const selectField = (event) => setField(event.option ? event.option.value : event.target.value);

  const saveValue = (event) => {
    setValue(event.target.value);
  };

  const filtersToMap = Object.keys(filters)
    .filter((key) => filters[key].remove === false && key !== "bbox" && key !== "datetime")
    .toSorted();

  const save = (event) => {
    event.preventDefault();
    event.stopPropagation();

    onAdd(field, value);
    setValue("");
    setField("");
  };

  useEffect(() => {
    if (Object.keys(filters).length !== 0) {
      const newChangedValue = {};
      Object.keys(filters).forEach((key) => {
        if (filters[key] && filters[key].value !== undefined) {
          newChangedValue[key] = { value: filters[key].value };
        }
      });
      setChangedValue(newChangedValue);
    }
  }, [isOpen]);

  useEffect(() => {
    setValue("ascending");
    setField("");
  }, [filters]);

  const noOp = (event) => {
    event.preventDefault();
    event.stopPropagation();
  };

  const overwriteFilters = (item) => () => {
    const updatedFilterValue = { ...changedValue };
    onAdd(item, updatedFilterValue[item].value);
  };

  return (
    <Form onSubmit={noOp}>
      <p className="text-muted text-uppercase">{t("field")}</p>
      <Row>
        <Col md="5">
          <FormGroup>
            <Input
              type="select"
              size="sm"
              name="field"
              className={`mr-2${field === "" ? " text-muted" : ""}`}
              value={field}
              onChange={selectField}
            >
              <option value="" className="d-none">
                {t("none")}
              </option>
              {Object.keys(fields)
                .toSorted()
                .map((f) => (
                  <option value={f} key={f}>
                    {fields[f]}
                  </option>
                ))}
            </Input>
          </FormGroup>
        </Col>
        <Col md="5">
          <FormGroup>
            <ValueField
              valueKey={field}
              value={value}
              saveValue={saveValue}
              enumKeys={["ascending", "descending"]}
              save={save}
              disabled={field === ""}
            />
          </FormGroup>
        </Col>
        <Col md="2">
          <Button color="primary" size="sm" disabled={field === ""} onClick={save}>
            {t("add")}
          </Button>
        </Col>
      </Row>
      <>
        {filtersToMap.map((key) => (
          <Row key={key}>
            <Col md="5">
              <Input
                type="text"
                size="sm"
                name="selectedField"
                id={`input1-${key}`}
                className="mr-2"
                disabled="true"
                defaultValue={titleForFilter[key]}
              />
            </Col>
            <Col md="5">
              <FormGroup>
                <FilterValueField
                  filterKey={key}
                  filters={filters}
                  setChangedValue={setChangedValue}
                  changedValue={changedValue}
                />
              </FormGroup>
            </Col>
            <Col md="2">
              <ButtonGroup>
                <Button
                  color="primary"
                  size="sm"
                  style={{ minWidth: "40px" }}
                  onClick={overwriteFilters(key)}
                  disabled={
                    changedValue[key]
                      ? !changedValue[key].value || changedValue[key].value === filters[key].value
                      : true
                  }
                >
                  {"\u2713"}
                </Button>
                <Button
                  color="danger"
                  size="sm"
                  style={{ minWidth: "40px" }}
                  onClick={deleteFilters(key)}
                >
                  {"\u2716"}
                </Button>
              </ButtonGroup>
            </Col>
          </Row>
        ))}
      </>
    </Form>
  );
};

FieldFilter.displayName = "FieldFilter";

FieldFilter.propTypes = {
  fields: PropTypes.objectOf(PropTypes.string).isRequired,
  onAdd: PropTypes.func.isRequired,
  // eslint-disable-next-line react/forbid-prop-types
  filters: PropTypes.object.isRequired,
  deleteFilters: PropTypes.func.isRequired,
  // eslint-disable-next-line react/forbid-prop-types
  titleForFilter: PropTypes.objectOf(PropTypes.string).isRequired,
  isOpen: PropTypes.bool,
};

FieldFilter.defaultProps = {
  isOpen: false,
};

export default FieldFilter;
