import React from "react";
import { Row, Col, Collapse } from "reactstrap";
import type { Filters } from "../types";

import FieldFilter from "./Fields";

export interface EditorBodyProps {
  isOpen?: boolean;
  fields?: Record<string, string>;
  filters: Filters;
  onAdd?: (field: string, value: string) => void;
  deleteFilters: (field: string) => () => void;
  titleForFilter: Record<string, string>;
}

const EditorBody = ({
  isOpen = false,
  fields = {},
  filters,
  onAdd = () => {},
  deleteFilters,
  titleForFilter,
}: EditorBodyProps) => {
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
                  {} as Record<string, string>,
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

export default EditorBody;
