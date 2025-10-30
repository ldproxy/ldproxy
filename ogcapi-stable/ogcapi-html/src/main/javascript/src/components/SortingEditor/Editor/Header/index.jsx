import React from "react";
import PropTypes from "prop-types";

import { Button, Row, Col } from "reactstrap";

import Badge from "../../Badge";

const EditorHeader = ({ isOpen, setOpen, isEnabled, filters, save, cancel, onRemove }) => {
  const toggle = (event) => {
    event.target.blur();

    setOpen(!isOpen);
  };

  return (
    <>
      <Row className="mb-3">
        <Col md="1" className="d-flex flex-row justify-content-start align-items-center flex-wrap">
          <span className="font-weight-bold">Sorting</span>
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
              {isOpen ? "Apply" : "Edit "}
            </Button>
          )}
          {isOpen && (
            <Button color="danger" size="sm" className="ml-1 py-0" onClick={cancel}>
              Cancel
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
