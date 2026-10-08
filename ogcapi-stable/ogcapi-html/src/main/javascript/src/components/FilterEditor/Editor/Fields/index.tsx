import React, { useState, useEffect } from "react";
import "bootstrap/dist/css/bootstrap.min.css";
import { Button, ButtonGroup, Form, FormGroup, Input, Row, Col } from "reactstrap";
import { useTranslation } from "react-i18next";
import type { ChangedValue, Code, Filters } from "../../types";
import FilterValueField from "./FilterValueField";
import ValueField from "./ValueField";

export interface FieldFilterProps {
  fields: Record<string, string>;
  onAdd: (field: string, value: string) => void;
  filters: Filters;
  deleteFilters: (field: string) => () => void;
  code: Code;
  titleForFilter: Record<string, string>;
  integerKeys: string[];
  booleanProperty: string[];
  isOpen?: boolean;
}

const FieldFilter = ({
  fields,
  onAdd,
  filters,
  deleteFilters,
  code,
  titleForFilter,
  integerKeys,
  booleanProperty,
  isOpen = false,
}: FieldFilterProps) => {
  const [field, setField] = useState("");
  const [value, setValue] = useState("");
  const [changedValue, setChangedValue] = useState<ChangedValue>({} as ChangedValue);
  const { t } = useTranslation();

  const selectField = (event: { option?: { value: string } } & React.ChangeEvent<HTMLInputElement>) =>
    setField(event.option ? event.option.value : event.target.value);

  useEffect(() => {
    if (Object.keys(filters).length !== 0) {
      const newChangedValue: ChangedValue = {};
      Object.keys(filters).forEach((key) => {
        if (filters[key] && filters[key].value !== undefined) {
          newChangedValue[key] = { filterKey: key, value: filters[key].value };
        }
      });
      setChangedValue(newChangedValue);
    }
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [isOpen]);

  const saveValue = (event: React.ChangeEvent<HTMLInputElement>) => {
    setValue(event.target.value);
  };

  const filtersToMap = Object.keys(filters)
    .filter((key) => filters[key].remove === false && key !== "bbox" && key !== "datetime")
    .toSorted();
  const enumKeys = Object.keys(code);

  const save = (event: React.SyntheticEvent) => {
    event.preventDefault();
    event.stopPropagation();

    onAdd(field, value);
    setValue("");
    setField("");
  };

  const noOp = (event: React.SyntheticEvent) => {
    event.preventDefault();
    event.stopPropagation();
  };

  const overwriteFilters = (item: string) => () => {
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
              bsSize="sm"
              name="field"
              className={`custom-select custom-select-sm mr-2${field === "" ? " text-muted" : ""}`}
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
                bsSize="sm"
                name="selectedField"
                id={`input1-${key}`}
                className="mr-2"
                disabled
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
                  {"✓"}
                </Button>
                <Button
                  color="danger"
                  size="sm"
                  style={{ minWidth: "40px" }}
                  onClick={deleteFilters(key)}
                >
                  {"✖"}
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

export default FieldFilter;
