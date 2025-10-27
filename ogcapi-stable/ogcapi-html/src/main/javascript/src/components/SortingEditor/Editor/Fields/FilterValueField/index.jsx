import React, { useEffect } from "react";
import PropTypes from "prop-types";
import { Input } from "reactstrap";
import { useTranslation } from "react-i18next";
import i18n from "../../../../../i18n";
import { fetchTranslations } from "../../../../../fetchTranslations";

const FilterValueField = ({
  filterKey,
  filters,
  changedValue,
  setChangedValue,
  overwriteFilters,
}) => {
  const { t } = useTranslation();

  useEffect(() => {
    fetchTranslations("de").then((res) => {
      i18n.addResourceBundle("de", "translation", res.translation, true, true);
      i18n.changeLanguage("de");
    });
  }, []);

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
      onBlur={() => overwriteFilters(filterKey)()}
    >
      <option value="ascending">{t("Ascending")}</option>
      <option value="descending">{t("Descending")}</option>
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
  overwriteFilters: PropTypes.func.isRequired,
};
