import React, { useEffect } from "react";
import PropTypes from "prop-types";
import { Button, Row, Col } from "reactstrap";
import { useTranslation } from "react-i18next";
import i18n from "../../../../i18n";
import { fetchTranslations } from "../../../../fetchTranslations";

import Badge from "../../Badge";

const EditorHeader = ({ isOpen, setOpen, isEnabled, filters, save, cancel, onRemove }) => {
  const { t } = useTranslation();

  useEffect(() => {
    fetchTranslations("de").then((res) => {
      i18n.addResourceBundle("de", "translation", res.translation, true, true);
      i18n.changeLanguage("de");
    });
  }, []);

  const toggle = (event) => {
    event.target.blur();

    setOpen(!isOpen);
  };

  return (
    <>
      <Row className="mb-3">
        <Col md="3" className="d-flex flex-row justify-content-start align-items-center flex-wrap">
          <span className="mr-2 font-weight-bold">{t("Sorting")}</span>
          {isEnabled && (
            <Button
              color={isOpen ? "primary" : "secondary"}
              outline={!isOpen}
              size="sm"
              className="py-0"
              onClick={isOpen ? save : toggle}
            >
              {isOpen ? t("Apply") : t("Edit")}
            </Button>
          )}
          {isOpen && (
            <Button color="danger" size="sm" className="ml-1 py-0" onClick={cancel}>
              {t("Cancel")}
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
                isEditable={isOpen}
                onRemove={onRemove}
              />
            ))}
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
  onRemove: PropTypes.func.isRequired,
};

EditorHeader.defaultProps = {
  isOpen: false,
};

export default EditorHeader;
