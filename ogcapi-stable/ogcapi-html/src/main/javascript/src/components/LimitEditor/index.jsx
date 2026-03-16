import React, { useEffect, useMemo, useState } from "react";
import PropTypes from "prop-types";
import qs from "qs";
import { useTranslation } from "react-i18next";
import { Button, Col, Row, Collapse, Input } from "reactstrap";
import i18n from "../../i18n";
import "../FilterEditor/Badge/style.css";

const parsePositiveInteger = (value) => {
  if (value === null || typeof value === "undefined" || value === "") {
    return null;
  }

  const parsed = Number.parseInt(value, 10);
  if (!Number.isInteger(parsed) || parsed <= 0) {
    return null;
  }

  return parsed;
};

const LimitEditor = ({ limitOptions = [], allowCustomLimit = false, defaultLimit = null }) => {
  const { t } = useTranslation();
  // eslint-disable-next-line no-undef, no-underscore-dangle
  const { language, translations } = globalThis._limit_selector;

  useEffect(() => {
    Object.entries(translations).forEach(([key, value]) => {
      i18n.addResourceBundle(language, "translation", { [key]: value }, true, true);
    });
  }, [language, translations]);

  const initialQuery = useMemo(
    () =>
      qs.parse(window.location.search, {
        ignoreQueryPrefix: true,
      }),
    []
  );

  const normalizedOptions = useMemo(
    () =>
      [...new Set((limitOptions || []).map(parsePositiveInteger).filter((v) => Number.isInteger(v)))].sort(
        (a, b) => a - b
      ),
    [limitOptions]
  );
  const hasConfiguredSelectOptions = normalizedOptions.length > 0;

  const normalizedDefault = parsePositiveInteger(defaultLimit);
  const initialApplied = parsePositiveInteger(initialQuery.limit) || normalizedDefault;

  const optionsWithDefaults = useMemo(() => {
    if (!hasConfiguredSelectOptions) {
      return [];
    }

    const values = [...normalizedOptions];
    if (normalizedDefault && !values.includes(normalizedDefault)) {
      values.push(normalizedDefault);
    }
    if (initialApplied && !values.includes(initialApplied)) {
      values.push(initialApplied);
    }
    return values.sort((a, b) => a - b);
  }, [hasConfiguredSelectOptions, normalizedOptions, normalizedDefault, initialApplied]);

  const getSelectValueForLimit = (limit) => {
    if (!optionsWithDefaults.length) {
      return "";
    }

    if (Number.isInteger(limit) && optionsWithDefaults.includes(limit)) {
      return String(limit);
    }

    return allowCustomLimit ? "" : String(optionsWithDefaults[0]);
  };

  const [isOpen, setOpen] = useState(false);
  const [appliedLimit, setAppliedLimit] = useState(initialApplied);
  const [draftLimit, setDraftLimit] = useState(initialApplied);
  const [customInput, setCustomInput] = useState(initialApplied ? String(initialApplied) : "");
  const [selectedOption, setSelectedOption] = useState(getSelectValueForLimit(initialApplied));

  const isDefaultApplied = Number.isInteger(normalizedDefault)
    ? appliedLimit === normalizedDefault
    : !Number.isInteger(appliedLimit);
  const isDraftChanged = draftLimit !== appliedLimit;
  const showBadge = !isDefaultApplied || (isOpen && isDraftChanged);
  const badgeValue = isOpen && isDraftChanged ? draftLimit : appliedLimit;
  const badgeColor = isOpen && isDraftChanged ? "success" : "primary";

  const toggle = (event) => {
    event.target.blur();
    setDraftLimit(appliedLimit);
    setCustomInput(appliedLimit ? String(appliedLimit) : "");
    setSelectedOption(getSelectValueForLimit(appliedLimit));
    setOpen(!isOpen);
  };

  const cancel = (event) => {
    event.target.blur();
    setDraftLimit(appliedLimit);
    setCustomInput(appliedLimit ? String(appliedLimit) : "");
    setSelectedOption(getSelectValueForLimit(appliedLimit));
    setOpen(false);
  };

  const save = (event) => {
    event.target.blur();

    const nextLimit = parsePositiveInteger(draftLimit);
    if (!nextLimit) {
      return;
    }

    const query = qs.parse(window.location.search, {
      ignoreQueryPrefix: true,
    });

    query.limit = nextLimit;
    delete query.offset;

    setAppliedLimit(nextLimit);
    window.location.search = qs.stringify(query, {
      addQueryPrefix: true,
    });
  };

  const onSelectChange = (event) => {
    const { value } = event.target;
    setSelectedOption(value);

    if (value === "") {
      setDraftLimit(parsePositiveInteger(customInput));
      return;
    }

    const next = parsePositiveInteger(value);
    setDraftLimit(next);
    setCustomInput(next ? String(next) : "");
  };

  const onCustomChange = (event) => {
    const nextValue = event.target.value;
    setCustomInput(nextValue);
    setDraftLimit(parsePositiveInteger(nextValue));
    if (optionsWithDefaults.length > 0 && allowCustomLimit) {
      setSelectedOption("");
    }
  };

  if (!allowCustomLimit && optionsWithDefaults.length === 0) {
    return null;
  }

  return (
    <>
      <Row className="mb-1">
        <Col md="1" className="d-flex flex-row justify-content-start align-items-center flex-wrap">
          <span className="font-weight-bold">{t("limit")}</span>
        </Col>
        <Col md="2" className="d-flex flex-row justify-content-start align-items-center flex-wrap">
          <Button
            color={isOpen ? "primary" : "secondary"}
            outline={!isOpen}
            size="sm"
            className="py-0"
            onClick={isOpen ? save : toggle}
            disabled={isOpen && !parsePositiveInteger(draftLimit)}
          >
            {isOpen ? t("apply") : t("edit")}
          </Button>
        </Col>
        <Col md="9" className="d-flex flex-row justify-content-start align-items-center flex-wrap">
          {showBadge && (
            <Button
              key={String(badgeValue)}
              color={badgeColor}
              disabled
              size="sm"
              className={`py-0 mr-1 my-1 ${isOpen && isDraftChanged ? "animate-flicker" : ""}`}
              style={{ opacity: "1" }}
            >
              {badgeValue}
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
            <Col md="8" className="px-0 d-flex align-items-center">
              {optionsWithDefaults.length > 0 && (
                <Input
                  type="select"
                  bsSize="sm"
                  className="d-inline-block mr-2"
                  style={{ width: "130px" }}
                  value={selectedOption}
                  onChange={onSelectChange}
                >
                  {allowCustomLimit && <option value="">{t("none")}</option>}
                  {optionsWithDefaults.map((value) => (
                    <option key={value} value={value}>
                      {value}
                    </option>
                  ))}
                </Input>
              )}
              {allowCustomLimit && (optionsWithDefaults.length === 0 || selectedOption === "") && (
                <Input
                  type="number"
                  bsSize="sm"
                  min="1"
                  step="1"
                  className="d-inline-block"
                  style={{ width: "130px" }}
                  value={customInput}
                  onChange={onCustomChange}
                  placeholder={t("limitPlaceholder")}
                />
              )}
            </Col>
          </Row>
        </div>
      </Collapse>
    </>
  );
};

LimitEditor.propTypes = {
  limitOptions: PropTypes.arrayOf(PropTypes.number),
  allowCustomLimit: PropTypes.bool,
  defaultLimit: PropTypes.number,
};

LimitEditor.defaultProps = {
  limitOptions: [],
  allowCustomLimit: false,
  defaultLimit: null,
};

export default LimitEditor;
