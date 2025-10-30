import React from "react";
import PropTypes from "prop-types";

import { Input } from "reactstrap";

const ValueField = ({ value, saveValue, save, disabled }) => (
  <Input
    type="select"
    size="sm"
    name="value"
    className="mr-2"
    value={value}
    onChange={saveValue}
    disabled={disabled}
    onKeyPress={(event) => {
      if (event.key === "Enter" && value !== "") {
        save(event);
      }
    }}
  >
    <option value="ascending">Ascending</option>
    <option value="descending">Descending</option>
  </Input>
);

export default ValueField;

ValueField.propTypes = {
  value: PropTypes.string.isRequired,
  saveValue: PropTypes.func.isRequired,
  save: PropTypes.func.isRequired,
  disabled: PropTypes.bool.isRequired,
};

ValueField.defaultProps = {};
