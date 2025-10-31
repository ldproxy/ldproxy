import React from "react";
import PropTypes from "prop-types";

import { Row, Col, Collapse } from "reactstrap";

import FieldFilter from "./Fields";

const EditorBody = ({ isOpen, fields, filters, onAdd, deleteFilters, titleForFilter }) => {
  return (
    <Collapse isOpen={isOpen} onEntered={() => {}}>
      <Row>
        <Col md="7">
          {Object.keys(fields).length > 0 && (
            <FieldFilter
              fields={Object.keys(fields)
                .filter((k) => !filters[k])
                .reduce(
                  (fs, k) => ({
                    ...fs,
                    [k]: fields[k],
                  }),
                  {}
                )}
              onAdd={onAdd}
              filters={filters}
              deleteFilters={deleteFilters}
              titleForFilter={titleForFilter}
              isOpen={isOpen}
            />
          )}
        </Col>
      </Row>
    </Collapse>
  );
};

EditorBody.displayName = "EditorBody";

EditorBody.propTypes = {
  isOpen: PropTypes.bool,
  fields: PropTypes.objectOf(PropTypes.string),
  // eslint-disable-next-line react/forbid-prop-types
  filters: PropTypes.object.isRequired,
  onAdd: PropTypes.func,
  deleteFilters: PropTypes.func.isRequired,
  // eslint-disable-next-line react/forbid-prop-types
  titleForFilter: PropTypes.objectOf(PropTypes.string).isRequired,
};

EditorBody.defaultProps = {
  isOpen: false,
  fields: {},
  onAdd: () => {},
};

export default EditorBody;
