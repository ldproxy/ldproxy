import React, { useState } from "react";
import PropTypes from "prop-types";
import "bootstrap/dist/css/bootstrap.min.css";
import { Button, ButtonGroup, Form, FormGroup, Input, Row, Col } from "reactstrap";
import { useTranslation } from "react-i18next";
import FilterValueField from "./FilterValueField";
import ValueField from "./ValueField";

const FieldFilter = ({
  fields,
  onAdd,
  filters,
  deleteFilters,
  code,
  titleForFilter,
  integerKeys,
  booleanProperty,
}) => {
  const [field, setField] = useState("");
  const [value, setValue] = useState("");
  const [changedValue, setChangedValue] = useState("");
  const { t } = useTranslation();

  const selectField = (event) => setField(event.option ? event.option.value : event.target.value);

  const saveValue = (event) => {
    setValue(event.target.value);
  };

  const filtersToMap = Object.keys(filters)
    .filter((key) => filters[key].remove === false && key !== "bbox" && key !== "datetime")
    .toSorted();
  const enumKeys = Object.keys(code);

  const save = (event) => {
    event.preventDefault();
    event.stopPropagation();

    onAdd(field, value);
    setValue("");
    setField("");
  };

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
              code={code}
              integerKeys={integerKeys}
              enumKeys={enumKeys}
              booleanProperty={booleanProperty}
              save={save}
              disabled={field === ""}
            />
          </FormGroup>
        </Col>
        <Col md="2">
          <Button color="primary" size="sm" disabled={field === "" || value === ""} onClick={save}>
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
                  code={code}
                  filterKey={key}
                  filters={filters}
                  setChangedValue={setChangedValue}
                  changedValue={changedValue}
                  enumKeys={enumKeys}
                  integerKeys={integerKeys}
                  booleanProperty={booleanProperty}
                  overwriteFilters={overwriteFilters(key)}
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
  code: PropTypes.object.isRequired,
  titleForFilter: PropTypes.objectOf(PropTypes.string).isRequired,
  integerKeys: PropTypes.arrayOf(PropTypes.string).isRequired,
  booleanProperty: PropTypes.arrayOf(PropTypes.string).isRequired,
};

FieldFilter.defaultProps = {};

export default FieldFilter;
