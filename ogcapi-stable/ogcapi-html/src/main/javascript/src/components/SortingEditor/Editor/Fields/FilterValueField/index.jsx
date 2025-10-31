import React from "react";
import PropTypes from "prop-types";
import { Input } from "reactstrap";
import { useTranslation } from "react-i18next";

const FilterValueField = ({ filterKey, filters, changedValue, setChangedValue }) => {
  const { t } = useTranslation();

  return (
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
    >
      <option value="ascending">{t("ascending")}</option>
      <option value="descending">{t("descending")}</option>
    </Input>
  );
};

export default FilterValueField;

FilterValueField.propTypes = {
  filterKey: PropTypes.string.isRequired,
  // eslint-disable-next-line react/forbid-prop-types
  filters: PropTypes.object.isRequired,
  // eslint-disable-next-line react/forbid-prop-types
  changedValue: PropTypes.object.isRequired,
  setChangedValue: PropTypes.func.isRequired,
};
