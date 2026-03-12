/* eslint-disable no-undef, no-underscore-dangle */
import React from "react";
import ReactDOM from "react-dom";
import LimitEditor from "../../components/LimitEditor";

if (globalThis._sortingfilter && globalThis._sortingfilter.limitcontainer) {
  ReactDOM.render(
    <React.StrictMode>
      <LimitEditor {...globalThis._sortingfilter} />
    </React.StrictMode>,
    document.getElementById(globalThis._sortingfilter.limitcontainer)
  );
}
