import React from "react";
// import PropTypes from 'prop-types';

import { Button } from "reactstrap";
import { useTranslation } from "react-i18next";
import "./style.css";

const FilterBadge = ({ field, value, isAdd, isRemove }) => {
  const { i18n, t } = useTranslation();

  let translatedValue = value;
  if (i18n.language === "de") {
    if (value === "ascending") translatedValue = t("Ascending");
    if (value === "descending") translatedValue = t("Descending");
  }

  const label = `${field}=${translatedValue}`;
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
