/* eslint-disable react/forbid-prop-types */
import React from "react";
import PropTypes from "prop-types";

import moment from "moment";

const doFormat = (date, hideTime) =>
  hideTime ? moment.utc(date).format("DD.MM.yyyy") : moment.utc(date).format("DD.MM.yyyy HH:mm:ss");

const Header = ({ start, end, title, hideTime }) => {
  const text = end
    ? `${doFormat(start, hideTime)} -- ${doFormat(end, hideTime)}`
    : doFormat(start, hideTime);

  return (
    <div
      style={{
        width: "100%",
        textAlign: "center",
        fontFamily: "Arial",
        margin: 5,
      }}
    >
      <b>{title}:</b>
      <div style={{ fontSize: 12 }}>{text}</div>
    </div>
  );
};

Header.propTypes = {
  start: PropTypes.any.isRequired,
  end: PropTypes.any,
  title: PropTypes.string,
  hideTime: PropTypes.bool.isRequired,
};

Header.defaultProps = {
  end: null,
  title: "Date/Time",
};

Header.displayName = "Header";

export default Header;
