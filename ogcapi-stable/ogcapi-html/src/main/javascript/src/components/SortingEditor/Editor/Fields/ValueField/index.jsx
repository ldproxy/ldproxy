import React from "react";
import PropTypes from "prop-types";

import { FormGroup, Label, Input, FormText } from "reactstrap";

const ValueField = ({
  value,
  saveValue,
  valueKey,
  code,
  integerKeys,
  enumKeys,
  booleanProperty,
  save,
  disabled,
}) => {
  switch (true) {
    case enumKeys.includes(valueKey):
      return (
        <Input
          type="select"
          size="sm"
          name="value"
          className="mr-2"
          value={value}
          onChange={saveValue}
          disabled={disabled}
        >
          <option value="" className="d-none">
            none
          </option>
          {Object.keys(code[valueKey]).map((item) => (
            <option value={code[valueKey][item]} key={item}>
              {code[valueKey][item]}
            </option>
          ))}
        </Input>
      );
    case integerKeys.includes(valueKey):
      return (
        <Input
          type="number"
          size="sm"
          name="value"
          placeholder="Enter Number"
          className="mr-2"
          value={value}
          disabled={disabled}
          onChange={saveValue}
          onKeyPress={(event) => {
            if (event.key === "Enter" && valueKey !== "" && value !== "") {
              save(event);
            }
          }}
        />
      );
    case booleanProperty.includes(valueKey):
      return (
        <FormGroup tag="fieldset">
          <FormGroup check inline>
            <Label check inline>
              <Input
                type="radio"
                name="value"
                value="true"
                disabled={disabled}
                checked={value === "true"}
                onChange={saveValue}
              />{" "}
              True
            </Label>
          </FormGroup>
          <FormGroup check inline>
            <Label check>
              <Input
                type="radio"
                name="value"
                value="false"
                disabled={disabled}
                checked={value === "false"}
                onChange={saveValue}
              />{" "}
              False
            </Label>
          </FormGroup>
        </FormGroup>
      );

    default:
      return (
        <>
          <Input
            type="text"
            size="sm"
            name="value"
            placeholder="filter pattern"
            className="mr-2"
            disabled={disabled}
            value={value}
            onChange={saveValue}
            onKeyPress={(event) => {
              if (event.key === "Enter" && valueKey !== "" && value !== "") {
                save(event);
              }
            }}
          />
          <FormText>Use * as wildcard</FormText>
        </>
      );
  }
};

export default ValueField;

ValueField.propTypes = {
  // eslint-disable-next-line react/forbid-prop-types
  code: PropTypes.object.isRequired,
  enumKeys: PropTypes.arrayOf(PropTypes.string).isRequired,
  integerKeys: PropTypes.arrayOf(PropTypes.string).isRequired,
  valueKey: PropTypes.string.isRequired,
  value: PropTypes.string.isRequired,
  saveValue: PropTypes.func.isRequired,
  booleanProperty: PropTypes.arrayOf(PropTypes.string).isRequired,
  save: PropTypes.func.isRequired,
  disabled: PropTypes.bool.isRequired,
};

ValueField.defaultProps = {};
