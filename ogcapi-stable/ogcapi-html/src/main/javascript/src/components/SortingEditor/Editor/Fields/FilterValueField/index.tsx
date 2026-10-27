import React from "react";
import { Input } from "reactstrap";
import { useTranslation } from "react-i18next";
import type { ChangedValue, Filters } from "../../../types";

export interface FilterValueFieldProps {
  filterKey: string;
  filters: Filters;
  changedValue: ChangedValue;
  setChangedValue: (value: ChangedValue) => void;
}

const FilterValueField = ({ filterKey, filters, changedValue, setChangedValue }: FilterValueFieldProps) => {
  const { t } = useTranslation();

  return (
    <Input
      type="select"
      bsSize="sm"
      name="value"
      className="custom-select custom-select-sm"
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
    >
      <option value="ascending">{t("ascending")}</option>
      <option value="descending">{t("descending")}</option>
    </Input>
  );
};

export default FilterValueField;
