import React from "react";
import PropTypes from "prop-types";
import { Button, Row, Col } from "reactstrap";
import { useTranslation } from "react-i18next";

import Badge from "../../Badge";

const EditorHeader = ({ isOpen, setOpen, isEnabled, filters, save, cancel }) => {
  const { t } = useTranslation();

  const toggle = (event) => {
    event.target.blur();

    setOpen(!isOpen);
  };

  return (
    <>
      <Row className="mb-1">
        <Col md="1" className="d-flex flex-row justify-content-start align-items-center flex-wrap">
          <span className="font-weight-bold">{t("sorting")}</span>
        </Col>
        <Col md="2" className="d-flex flex-row justify-content-start align-items-center flex-wrap">
          {isEnabled && (
            <Button
              color={isOpen ? "primary" : "secondary"}
              outline={!isOpen}
              size="sm"
              className="py-0"
              onClick={isOpen ? save : toggle}
            >
              {isOpen ? t("apply") : t("edit")}
            </Button>
          )}
        </Col>
        <Col md="9" className="d-flex flex-row justify-content-start align-items-center flex-wrap">
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
          md="1"
          className="d-flex flex-row justify-content-start align-items-center flex-wrap"
        />
        <Col md="2" className="d-flex flex-row justify-content-start align-items-center flex-wrap">
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

EditorHeader.propTypes = {
  isOpen: PropTypes.bool,
  setOpen: PropTypes.func.isRequired,
  isEnabled: PropTypes.bool.isRequired,
  // eslint-disable-next-line react/forbid-prop-types
  filters: PropTypes.object.isRequired,
  save: PropTypes.func.isRequired,
  cancel: PropTypes.func.isRequired,
};

EditorHeader.defaultProps = {
  isOpen: false,
};

export default EditorHeader;
