/* eslint-disable no-undef, no-underscore-dangle */
import React from "react";
import { createRoot } from "react-dom/client";
import SortingEditor from "../../components/SortingEditor";

if (globalThis._sortingfilter && global._sortingfilter.sortingcontainer) {
  createRoot(document.getElementById(global._sortingfilter.sortingcontainer)).render(
    <React.StrictMode>
      <SortingEditor {...globalThis._sortingfilter} />
    </React.StrictMode>
  );
}
