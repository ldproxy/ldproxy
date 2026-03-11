import React, { useEffect, useMemo, useState } from "react";
import qs from "qs";
import { useTranslation } from "react-i18next";
import { Button, Col, Row, Collapse } from "reactstrap";
import i18n from "../../i18n";
import { useApiInfo } from "../SortingEditor/hooks";
import "../FilterEditor/Badge/style.css";

const DEFAULT_CRS_URI = "http://www.opengis.net/def/crs/OGC/1.3/CRS84";

const normalizeCrs = (crs) => {
  if (!crs) {
    return DEFAULT_CRS_URI;
  }
  if (crs === "CRS84" || crs === "OGC:CRS84") {
    return DEFAULT_CRS_URI;
  }
  if (crs.endsWith("/OGC/0/CRS84") || crs.endsWith("/OGC/1.3/CRS84")) {
    return DEFAULT_CRS_URI;
  }
  return crs;
};

const getBaseUrl = () => {
  let baseUrl = new URL(window.location.href);
  if (process.env.NODE_ENV !== "production") {
    baseUrl = new URL("https://demo.ldproxy.net/strassen/collections/abschnitteaeste/items?f=html");
  }
  return baseUrl;
};

const getCollectionUrl = (baseUrl) => {
  const url = new URL(baseUrl.href);
  url.search = "?f=json";
  url.pathname = url.pathname.replace(/\/items\/?$/, "");
  return url;
};

const getCrsLabel = (crs) => {
  const epsg = crs.match(/\/def\/crs\/EPSG\/0\/(\d+)$/);
  if (epsg) {
    return `EPSG:${epsg[1]}`;
  }

  if (crs.endsWith("/OGC/1.3/CRS84")) {
    return "OGC:CRS84";
  }

  if (crs.endsWith("/OGC/0/CRS84h")) {
    return "OGC:CRS84h";
  }

  return crs;
};

const CrsEditor = () => {
  const { t } = useTranslation();
  // eslint-disable-next-line no-undef, no-underscore-dangle
  const { language, translations } = globalThis._sortingfilter;

  const [crsValues, setCrsValues] = useState([]);
  const [isOpen, setOpen] = useState(false);

  const collectionUrl = useMemo(() => getCollectionUrl(getBaseUrl()), []);
  const { obj: collection, isLoaded, error } = useApiInfo(collectionUrl);

  useEffect(() => {
    Object.entries(translations).forEach(([key, value]) => {
      i18n.addResourceBundle(language, "translation", { [key]: value }, true, true);
    });
  }, [language, translations]);

  useEffect(() => {
    if (!isLoaded || !collection) {
      return;
    }

    const values = [
      ...new Set(
        (collection.crs || [])
          .filter((crs) => !crs.startsWith("#"))
          .map((crs) => normalizeCrs(crs))
      ),
    ];
    setCrsValues(values);
  }, [collection, isLoaded]);

  const initialQuery = useMemo(
    () =>
      qs.parse(window.location.search, {
        ignoreQueryPrefix: true,
      }),
    []
  );

  const [appliedCrs, setAppliedCrs] = useState(normalizeCrs(initialQuery.crs));
  const [draftCrs, setDraftCrs] = useState(normalizeCrs(initialQuery.crs));

  const isDefaultApplied = appliedCrs === DEFAULT_CRS_URI;
  const isDraftChanged = draftCrs !== appliedCrs;

  let badgeColor = "primary";
  if (isOpen && isDraftChanged) {
    badgeColor = "success";
  }

  const badgeValue = isOpen && isDraftChanged ? draftCrs : appliedCrs;
  const showBadge = !isDefaultApplied || (isOpen && isDraftChanged);

  const toggle = (event) => {
    event.target.blur();
    setDraftCrs(appliedCrs);
    setOpen(!isOpen);
  };

  const save = (event) => {
    event.target.blur();

    const query = qs.parse(window.location.search, {
      ignoreQueryPrefix: true,
    });

    if (draftCrs === DEFAULT_CRS_URI) {
      delete query.crs;
    } else {
      query.crs = draftCrs;
    }

    setAppliedCrs(draftCrs);

    window.location.search = qs.stringify(query, {
      addQueryPrefix: true,
    });
  };

  const cancel = (event) => {
    event.target.blur();
    setDraftCrs(appliedCrs);
    setOpen(false);
  };

  const onDraftChange = (event) => {
    const { value } = event.target;
    setDraftCrs(value);
  };

  if (error) {
    return <div>{t("error")}</div>;
  }

  if (crsValues.length <= 1) {
    return null;
  }

  return (
    <>
      <Row className="mb-1">
        <Col md="1" className="d-flex flex-row justify-content-start align-items-center flex-wrap">
          <span className="font-weight-bold">{t("crs")}</span>
        </Col>
        <Col md="2" className="d-flex flex-row justify-content-start align-items-center flex-wrap">
          <Button
            color={isOpen ? "primary" : "secondary"}
            outline={!isOpen}
            size="sm"
            className="py-0"
            onClick={isOpen ? save : toggle}
          >
            {isOpen ? t("apply") : t("edit")}
          </Button>
        </Col>
        <Col md="9" className="d-flex flex-row justify-content-start align-items-center flex-wrap">
          {showBadge && (
            <Button
              key={badgeValue}
              color={badgeColor}
              disabled
              size="sm"
              className={`py-0 mr-1 my-1 ${isOpen && isDraftChanged ? "animate-flicker" : ""}`}
              style={{ opacity: "1" }}
            >
              {getCrsLabel(badgeValue)}
            </Button>
          )}
        </Col>
      </Row>
      <Row className="mb-3">
        <Col md="1" className="d-flex flex-row justify-content-start align-items-center flex-wrap" />
        <Col md="2" className="d-flex flex-row justify-content-start align-items-center flex-wrap">
          {isOpen && (
            <Button color="danger" size="sm" className="py-0" onClick={cancel}>
              {t("cancel")}
            </Button>
          )}
        </Col>
      </Row>
      <Collapse isOpen={isOpen}>
        <div className="pt-2 pb-3">
          <Row className="m-0">
            <Col md="7" className="px-0">
              <select
                className="form-control form-control-sm d-inline-block w-auto"
                style={{ minWidth: "260px" }}
                value={draftCrs}
                onChange={onDraftChange}
              >
                <option value={DEFAULT_CRS_URI}>
                  {`${getCrsLabel(DEFAULT_CRS_URI)} (${t("crsDefault")})`}
                </option>
                {crsValues
                  .filter((crs) => crs !== DEFAULT_CRS_URI)
                  .map((crs) => (
                    <option key={crs} value={crs}>
                      {getCrsLabel(crs)}
                    </option>
                  ))}
              </select>
            </Col>
          </Row>
        </div>
      </Collapse>
    </>
  );
};

export default CrsEditor;
