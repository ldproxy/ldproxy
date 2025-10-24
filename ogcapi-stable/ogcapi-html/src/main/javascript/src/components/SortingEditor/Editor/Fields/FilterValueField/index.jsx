import React from "react";
import PropTypes from "prop-types";

import { FormGroup, Label, Input } from "reactstrap";

const FilterValueField = ({
  code,
  filterKey,
  filters,
  changedValue,
  setChangedValue,
  enumKeys,
  integerKeys,
  booleanProperty,
  overwriteFilters,
}) => {
  switch (true) {
    case enumKeys.includes(filterKey):
      return (
        <Input
          type="select"
          size="sm"
          name="value"
          className="mr-2"
          defaultValue={filters[filterKey].value}
          onChange={(e) =>
            setChangedValue({
              ...changedValue,
              [filterKey]: {
                filterKey,
                value: e.target.value,
              },
            })
          }
        >
          {Object.keys(code[filterKey]).map((item) => (
            <option value={code[filterKey][item]} key={item}>
              {code[filterKey][item]}
            </option>
          ))}
        </Input>
      );
    case integerKeys.includes(filterKey):
      return (
        <Input
          type="number"
          size="sm"
          name="value2"
          id={`input-${filterKey}`}
          defaultValue={filters[filterKey].value}
          className="mr-2"
          onChange={(e) =>
            setChangedValue({
              ...changedValue,
              [filterKey]: {
                filterKey,
                value: e.target.value,
              },
            })
          }
          onFocus={(e) => e.target.select()}
          onKeyPress={(e) => {
            if (
              e.key === "Enter" &&
              changedValue[filterKey] &&
              changedValue[filterKey].value &&
              changedValue[filterKey].value !== filters[filterKey].value
            ) {
              overwriteFilters(filterKey);
            }
          }}
        />
      );
    case booleanProperty.includes(filterKey):
      return (
        <FormGroup tag="fieldset">
          <FormGroup check inline>
            <Label check inline>
              <Input
                type="radio"
                name="value"
                value="true"
                defaultChecked={filters[filterKey].value === "true"}
                onChange={(e) =>
                  setChangedValue({
                    ...changedValue,
                    [filterKey]: {
                      filterKey,
                      value: e.target.value,
                    },
                  })
                }
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
                defaultChecked={filters[filterKey].value === "false"}
                onChange={(e) =>
                  setChangedValue({
                    ...changedValue,
                    [filterKey]: {
                      filterKey,
                      value: e.target.value,
                    },
                  })
                }
              />{" "}
              False
            </Label>
          </FormGroup>
        </FormGroup>
      );
    default:
      return (
        <Input
          type="text"
          size="sm"
          name="selectedValue"
          id={`input-${filterKey}`}
          className="mr-2"
          defaultValue={filters[filterKey].value}
          onChange={(e) =>
            setChangedValue({
              ...changedValue,
              [filterKey]: {
                filterKey,
                value: e.target.value,
              },
            })
          }
          onFocus={(e) => e.target.select()}
          onKeyPress={(e) => {
            if (
              e.key === "Enter" &&
              changedValue[filterKey] &&
              changedValue[filterKey].value &&
              changedValue[filterKey].value !== filters[filterKey].value
            ) {
              overwriteFilters(filterKey);
            }
          }}
        />
      );
  }
};

export default FilterValueField;

FilterValueField.propTypes = {
  filterKey: PropTypes.string.isRequired,
  // eslint-disable-next-line react/forbid-prop-types
  filters: PropTypes.object.isRequired,
  // eslint-disable-next-line react/forbid-prop-types
  code: PropTypes.object.isRequired,
  changedValue: PropTypes.oneOfType([PropTypes.object, PropTypes.string]).isRequired,
  setChangedValue: PropTypes.func.isRequired,
  enumKeys: PropTypes.arrayOf(PropTypes.string).isRequired,
  integerKeys: PropTypes.arrayOf(PropTypes.string).isRequired,
  booleanProperty: PropTypes.arrayOf(PropTypes.string).isRequired,
  overwriteFilters: PropTypes.func.isRequired,
};

FilterValueField.defaultProps = {};
