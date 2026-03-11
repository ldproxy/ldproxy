/* eslint-disable no-undef, no-underscore-dangle */
import React from "react";
import ReactDOM from "react-dom";
import CrsEditor from "../../components/CrsEditor";

if (globalThis._sortingfilter && globalThis._sortingfilter.crscontainer) {
  ReactDOM.render(
    <React.StrictMode>
      <CrsEditor {...globalThis._sortingfilter} />
    </React.StrictMode>,
    document.getElementById(globalThis._sortingfilter.crscontainer)
  );
}
