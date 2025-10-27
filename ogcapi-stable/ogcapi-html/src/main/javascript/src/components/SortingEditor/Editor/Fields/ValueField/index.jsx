import React, { useEffect } from "react";
import PropTypes from "prop-types";
import { Input } from "reactstrap";
import { useTranslation } from "react-i18next";
import i18n from "../../../../../i18n";
import { fetchTranslations } from "../../../../../fetchTranslations";

const ValueField = ({ value, saveValue, save, disabled }) => {
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
      <option value="" className="d-none">
        {t("None")}
      </option>
      <option value="ascending">{t("Ascending")}</option>
      <option value="descending">{t("Descending")}</option>
    </Input>
  );
};

export default ValueField;

ValueField.propTypes = {
  value: PropTypes.string.isRequired,
  saveValue: PropTypes.func.isRequired,
  save: PropTypes.func.isRequired,
  disabled: PropTypes.bool.isRequired,
};

ValueField.defaultProps = {};
