/* eslint-disable no-undef, no-underscore-dangle */
import React from "react";
import { createRoot } from "react-dom/client";
import CrsEditor from "../../components/CrsEditor";

if (globalThis._crs_selector && globalThis._crs_selector.container) {
  createRoot(document.getElementById(globalThis._crs_selector.container)).render(
    <React.StrictMode>
      <CrsEditor {...globalThis._crs_selector} />
    </React.StrictMode>
  );
}
