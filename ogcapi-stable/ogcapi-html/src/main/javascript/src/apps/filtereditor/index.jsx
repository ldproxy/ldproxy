/* eslint-disable no-undef, no-underscore-dangle */
import React from "react";
import { createRoot } from "react-dom/client";
import FilterEditor from "../../components/FilterEditor";

if (globalThis._filter && globalThis._filter.container) {
  createRoot(document.getElementById(global._filter.container)).render(
    <React.StrictMode>
      <FilterEditor {...globalThis._filter} />
    </React.StrictMode>
  );
}
