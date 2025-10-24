/* eslint-disable no-undef, no-underscore-dangle */
import React from "react";
import ReactDOM from "react-dom";
import SortingEditor from "../../components/SortingEditor";

if (globalThis._sortingfilter && global._sortingfilter.sortingcontainer) {
  ReactDOM.render(
    <React.StrictMode>
      <SortingEditor {...globalThis._sortingfilter} />
    </React.StrictMode>,
    document.getElementById(global._sortingfilter.sortingcontainer)
  );
}
