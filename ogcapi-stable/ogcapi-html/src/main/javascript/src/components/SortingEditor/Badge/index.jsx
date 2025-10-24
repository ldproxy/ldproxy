import React from "react";
// import PropTypes from 'prop-types';

import { Button } from "reactstrap";
import "./style.css";

const FilterBadge = ({ field, value, isAdd, isRemove }) => {
  const label = `${field}=${value}`;

  const button = (
    <Button
      key={value}
      // eslint-disable-next-line no-nested-ternary
      color={isAdd ? "success" : isRemove ? "danger" : "primary"}
      disabled
      size="sm"
      className={`py-0 mr-1 my-1 ${isAdd || isRemove ? "animate-flicker" : ""}`}
      style={{ opacity: "1" }}
    >
      {label}
    </Button>
  );

  return button;
};

FilterBadge.displayName = "FilterBadge";

FilterBadge.propTypes = {};

FilterBadge.defaultProps = {};

export default FilterBadge;
