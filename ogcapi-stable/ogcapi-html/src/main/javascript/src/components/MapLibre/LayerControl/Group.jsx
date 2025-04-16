/* eslint-disable no-nested-ternary */
import React from "react";
import PropTypes from "prop-types";
import { Collapse, Row } from "reactstrap";
import Layer from "./Layer";
import Header from "./Header";
import Separator from "./Separator";

const Group = ({ parent, style, level, isControlable, isOpened, isSelected, onSelect, onOpen }) => {
  if (parent.type === "radio-group") {
    return (
      <Collapse isOpen={isOpened(parent.id)}>
        {parent.entries.map((entry, i) => {
          return (
            <React.Fragment key={entry.id}>
              {i > 0 && <Separator />}
              <Row>
                <Layer
                  id={entry.id}
                  label={entry.label}
                  icons={[entry]}
                  isControlable={isControlable}
                  isSelected={isSelected}
                  onSelect={onSelect}
                  style={style}
                  level={level + 1}
                  radioGroup={parent.id}
                />
              </Row>
            </React.Fragment>
          );
        })}
      </Collapse>
    );
  }
  if (parent.type === "merge-group") {
    return (
      <Row
        style={{
          flexWrap: "nowrap",
        }}
      >
        <Layer
          id={parent.id}
          label={parent.label}
          icons={parent.entries}
          isControlable={isControlable}
          isSelected={isSelected}
          onSelect={onSelect}
          style={style}
          level={level}
        />
      </Row>
    );
  }
  if (parent.type === "group") {
    return (
      <Collapse isOpen={isOpened(parent.id)}>
        {parent.entries.map((entry, i) => {
          if (entry.isLayer) {
            return (
              <React.Fragment key={entry.id}>
                {i > 0 && <Separator />}
                <Row>
                  <Layer
                    id={entry.id}
                    label={entry.label}
                    icons={[entry]}
                    isControlable={isControlable && !entry.onlyLegend}
                    isSelected={isSelected}
                    onSelect={onSelect}
                    style={style}
                    level={level + 1}
                  />
                </Row>
              </React.Fragment>
            );
          }
          return (
            <React.Fragment key={entry.id}>
              {i > 0 && <Separator />}
              {entry.type !== "merge-group" && (
                <Header
                  id={entry.id}
                  label={entry.label}
                  level={level + 1}
                  check={entry.type !== "radio-group"}
                  isControlable={isControlable}
                  isOpened={isOpened}
                  isSelected={isSelected}
                  onOpen={onOpen}
                  onSelect={onSelect}
                />
              )}
              <Group
                parent={entry}
                style={style}
                level={level + 1}
                isControlable={isControlable && !entry.onlyLegend}
                isOpened={isOpened}
                isSelected={isSelected}
                onSelect={onSelect}
                onOpen={onOpen}
              />
            </React.Fragment>
          );
        })}
      </Collapse>
    );
  }

  return null;
};

Group.displayName = "Group";

Group.propTypes = {
  // eslint-disable-next-line react/forbid-prop-types
  parent: PropTypes.object.isRequired,
  // eslint-disable-next-line react/forbid-prop-types
  style: PropTypes.object.isRequired,
  level: PropTypes.number,
  isControlable: PropTypes.bool.isRequired,
  isOpened: PropTypes.func.isRequired,
  isSelected: PropTypes.func.isRequired,
  onSelect: PropTypes.func.isRequired,
  onOpen: PropTypes.func.isRequired,
};

Group.defaultProps = {
  level: 0,
};

export default Group;
