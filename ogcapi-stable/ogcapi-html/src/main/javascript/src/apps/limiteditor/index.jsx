/* eslint-disable no-undef, no-underscore-dangle */
import React from "react";
import ReactDOM from "react-dom";
import LimitEditor from "../../components/LimitEditor";

if (globalThis._limit_selector && globalThis._limit_selector.container) {
  ReactDOM.render(
    <React.StrictMode>
      <LimitEditor {...globalThis._limit_selector} />
    </React.StrictMode>,
    document.getElementById(globalThis._limit_selector.container)
  );
}
