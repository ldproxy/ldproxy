/* eslint-disable no-undef, no-underscore-dangle */
import React from "react";
import ReactDOM from "react-dom";
import CrsEditor from "../../components/CrsEditor";

if (globalThis._crs_selector && globalThis._crs_selector.container) {
  ReactDOM.render(
    <React.StrictMode>
      <CrsEditor {...globalThis._crs_selector} />
    </React.StrictMode>,
    document.getElementById(globalThis._crs_selector.container)
  );
}
