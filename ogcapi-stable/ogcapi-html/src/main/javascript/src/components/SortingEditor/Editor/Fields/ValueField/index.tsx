import React from "react";
import { Input } from "reactstrap";
import { useTranslation } from "react-i18next";

export interface ValueFieldProps {
  value: string;
  saveValue: (event: React.ChangeEvent<HTMLInputElement>) => void;
  save: (event: React.SyntheticEvent) => void;
  disabled: boolean;
}

const ValueField = ({ value, saveValue, save, disabled }: ValueFieldProps) => {
  const { t } = useTranslation();

  return (
    <Input
      type="select"
      bsSize="sm"
      name="value"
      className="custom-select custom-select-sm mr-2"
      value={value}
      onChange={saveValue}
      disabled={disabled}
      onKeyPress={(event) => {
        if (event.key === "Enter" && value !== "") {
          save(event);
        }
      }}
    >
      <option value="ascending">{t("ascending")}</option>
      <option value="descending">{t("descending")}</option>
    </Input>
  );
};

export default ValueField;
