import React from "react";
import PropTypes from "prop-types";
import { Input } from "reactstrap";

const FilterValueField = ({
  filterKey,
  filters,
  changedValue,
  setChangedValue,
  overwriteFilters,
}) => (
  <Input
    type="select"
    size="sm"
    name="value"
    value={changedValue[filterKey]?.value || filters[filterKey].value}
    onChange={(e) =>
      setChangedValue({
        ...changedValue,
        [filterKey]: {
          filterKey,
          value: e.target.value,
        },
      })
    }
    onBlur={() => overwriteFilters(filterKey)()}
  >
    <option value="ascending">Ascending</option>
    <option value="descending">Descending</option>
  </Input>
);

export default FilterValueField;

FilterValueField.propTypes = {
  filterKey: PropTypes.string.isRequired,
  // eslint-disable-next-line react/forbid-prop-types
  filters: PropTypes.object.isRequired,
  // eslint-disable-next-line react/forbid-prop-types
  changedValue: PropTypes.object.isRequired,
  setChangedValue: PropTypes.func.isRequired,
  overwriteFilters: PropTypes.func.isRequired,
};
