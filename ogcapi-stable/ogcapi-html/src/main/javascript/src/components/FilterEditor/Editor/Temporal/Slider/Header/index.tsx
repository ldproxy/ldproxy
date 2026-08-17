import React from "react";
import moment from "moment";

const doFormat = (date: moment.MomentInput, hideTime?: boolean): string =>
  hideTime ? moment.utc(date).format("DD.MM.yyyy") : moment.utc(date).format("DD.MM.yyyy HH:mm:ss");

export interface HeaderProps {
  start: moment.MomentInput;
  end?: moment.MomentInput | null;
  title?: string;
  hideTime?: boolean;
}

const Header = ({ start, end = null, title = "Date/Time", hideTime }: HeaderProps) => {
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

Header.displayName = "Header";

export default Header;
