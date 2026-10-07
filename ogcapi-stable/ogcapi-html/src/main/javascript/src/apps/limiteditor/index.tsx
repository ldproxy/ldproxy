import React from "react";
import { createRoot } from "react-dom/client";
import LimitEditor from "../../components/LimitEditor";

if (globalThis._limit_selector && globalThis._limit_selector.container) {
  createRoot(document.getElementById(globalThis._limit_selector.container)!).render(
    <React.StrictMode>
      <LimitEditor {...globalThis._limit_selector} />
    </React.StrictMode>,
  );
}
