import React from "react";
import { Button, Row, Col } from "reactstrap";
import { useTranslation } from "react-i18next";
import type { Filters } from "../../types";

import Badge from "../../Badge";

export interface EditorHeaderProps {
  isOpen?: boolean;
  setOpen: (isOpen: boolean) => void;
  isEnabled: boolean;
  filters: Filters;
  save: (event: React.SyntheticEvent) => void;
  cancel: (event: React.SyntheticEvent) => void;
}

const EditorHeader = ({ isOpen = false, setOpen, isEnabled, filters, save, cancel }: EditorHeaderProps) => {
  const { t } = useTranslation();

  const toggle = (event: React.MouseEvent<HTMLButtonElement>) => {
    (event.target as HTMLButtonElement).blur();

    setOpen(!isOpen);
  };

  return (
    <>
      <Row className="mb-1">
        <Col
          md="auto"
          className="d-flex flex-row justify-content-start align-items-center flex-wrap"
          style={{ width: "235px" }}
        >
          <span className="font-weight-bold text-nowrap">Filter</span>
        </Col>
        <Col md="auto" className="d-flex flex-row justify-content-start align-items-center flex-wrap">
          {isEnabled && (
            <Button
              color={isOpen ? "primary" : "secondary"}
              outline={!isOpen}
              size="sm"
              className="py-0"
              onClick={isOpen ? save : toggle}
            >
              {isOpen ? t("apply") : t("edit")}{" "}
            </Button>
          )}
        </Col>
        <Col className="d-flex flex-row justify-content-start align-items-center flex-wrap">
          {isEnabled &&
            Object.keys(filters).map((key) => (
              <Badge
                key={key}
                field={key}
                value={filters[key].value}
                isAdd={filters[key].add}
                isRemove={filters[key].remove}
              />
            ))}
        </Col>
      </Row>
      <Row className="mb-3">
        <Col
          md="auto"
          className="d-flex flex-row justify-content-start align-items-center flex-wrap"
          style={{ width: "235px" }}
        />
        <Col md="auto" className="d-flex flex-row justify-content-start align-items-center flex-wrap">
          {isOpen && (
            <Button color="danger" size="sm" className="py-0" onClick={cancel}>
              {t("cancel")}
            </Button>
          )}
        </Col>
      </Row>
    </>
  );
};

EditorHeader.displayName = "EditorHeader";

export default EditorHeader;
