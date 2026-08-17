import React from "react";
import { Button } from "reactstrap";
import "./style.css";

export interface FilterBadgeProps {
  field: string;
  value: string | number;
  isAdd?: boolean;
  isRemove?: boolean;
}

const FilterBadge = ({ field, value, isAdd, isRemove }: FilterBadgeProps) => {
  let arrow = "";
  if (value === "ascending") arrow = "↑";
  if (value === "descending") arrow = "↓";
  const label = `${field} ${arrow}`;

  return (
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
};

FilterBadge.displayName = "FilterBadge";

export default FilterBadge;
