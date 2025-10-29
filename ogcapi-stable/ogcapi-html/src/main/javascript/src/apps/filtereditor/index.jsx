/* eslint-disable no-undef, no-underscore-dangle */
import React from "react";
import ReactDOM from "react-dom";
import FilterEditor from "../../components/FilterEditor";

if (globalThis._filter && globalThis._filter.container) {
  ReactDOM.render(
    <React.StrictMode>
      <FilterEditor {...globalThis._filter} />
    </React.StrictMode>,
    document.getElementById(global._filter.container)
  );
}
