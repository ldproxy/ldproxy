import React from "react";
import { FormGroup, Label, Input, FormText } from "reactstrap";
import { useTranslation } from "react-i18next";
import type { Code } from "../../../types";

export interface ValueFieldProps {
  value: string;
  saveValue: (event: React.ChangeEvent<HTMLInputElement>) => void;
  valueKey: string;
  code: Code;
  integerKeys: string[];
  enumKeys: string[];
  booleanProperty: string[];
  save: (event: React.SyntheticEvent) => void;
  disabled: boolean;
}

const ValueField = ({
  value,
  saveValue,
  valueKey,
  code,
  integerKeys,
  enumKeys,
  booleanProperty,
  save,
  disabled,
}: ValueFieldProps) => {
  const { t } = useTranslation();

  switch (true) {
    case enumKeys.includes(valueKey):
      return (
        <Input
          type="select"
          bsSize="sm"
          name="value"
          className="custom-select custom-select-sm mr-2"
          value={value}
          onChange={saveValue}
          disabled={disabled}
        >
          <option value="" className="d-none">
            {t("none")}
          </option>
          {Object.keys(code[valueKey]).map((item) => (
            <option value={code[valueKey][Number(item)]} key={item}>
              {code[valueKey][Number(item)]}
            </option>
          ))}
        </Input>
      );
    case integerKeys.includes(valueKey):
      return (
        <Input
          type="number"
          bsSize="sm"
          name="value"
          placeholder="Enter Number"
          className="mr-2"
          value={value}
          disabled={disabled}
          onChange={saveValue}
          onKeyPress={(event) => {
            if (event.key === "Enter" && valueKey !== "" && value !== "") {
              save(event);
            }
          }}
        />
      );
    case booleanProperty.includes(valueKey):
      return (
        <FormGroup tag="fieldset">
          <FormGroup check inline>
            <Label check inline>
              <Input
                type="radio"
                name="value"
                value="true"
                disabled={disabled}
                checked={value === "true"}
                onChange={saveValue}
              />{" "}
              {t("true")}
            </Label>
          </FormGroup>
          <FormGroup check inline>
            <Label check>
              <Input
                type="radio"
                name="value"
                value="false"
                disabled={disabled}
                checked={value === "false"}
                onChange={saveValue}
              />{" "}
              {t("false")}
            </Label>
          </FormGroup>
        </FormGroup>
      );

    default:
      return (
        <>
          <Input
            type="text"
            bsSize="sm"
            name="value"
            placeholder={t("filterPattern")}
            className="mr-2"
            disabled={disabled}
            value={value}
            onChange={saveValue}
            onKeyPress={(event) => {
              if (event.key === "Enter" && valueKey !== "" && value !== "") {
                save(event);
              }
            }}
          />
          <FormText>{t("wildcard")}</FormText>
        </>
      );
  }
};

export default ValueField;
