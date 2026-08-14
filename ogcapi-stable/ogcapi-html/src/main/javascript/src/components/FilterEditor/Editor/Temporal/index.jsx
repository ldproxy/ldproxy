import React, { useState, useEffect, useCallback, useMemo } from "react";
import Datetime from "react-datetime";
import "react-datetime/css/react-datetime.css";
import "./style.css";
import PropTypes from "prop-types";
import { Button, ButtonGroup, Form, Input, Row, Col, FormText } from "reactstrap";
import moment from "moment";
import { useTranslation } from "react-i18next";
import Slider from "./Slider";

import { validateInstant, validatePeriod, errorInstant } from "./util";

// react-datetime-range-picker's own `input` prop never toggled react-datetime's `input` prop
// (which hides the text field entirely and shows the calendar permanently expanded) — it kept
// the field visible and just made it read-only-with-a-different-style. Replicated here directly
// via `inputProps`, since we render two plain <Datetime> instances instead of the old wrapper.
const readOnlyInputStyle = {
  cursor: "pointer",
  backgroundColor: "white",
  border: "1px solid #e2e2e2",
};

const fromFilterString = (filter) => {
  if (filter.indexOf("/") === -1) {
    return {
      start: filter,
      end: null,
    };
  }

  return {
    start: filter.split("/")[0],
    end:
      filter.split("/")[1].indexOf("P") === 0
        ? moment
            .utc(filter.split("/")[0])
            .add(moment.duration(filter.split("/")[1]))
            .format()
        : filter.split("/")[1],
  };
};

export const toTimeLabel = (filter) => {
  const datetime = fromFilterString(filter);

  if (!datetime.end) {
    return `datetime=${moment.utc(datetime.start).format("DD.MM.YY HH:mm:ss")}`;
  }
  return `datetime=${moment.utc(datetime.start).format("DD.MM.YY HH:mm:ss")} - ${moment
    .utc(datetime.end)
    .format("DD.MM.YY HH:mm:ss")}`;
};

const formatDate = (date) => {
  return moment.utc(date).format();
};

const TemporalFilter = ({ start, end = null, filter = null, onChange, filters, deleteFilters }) => {
  const min = start;
  const max = end;

  const minInstant = start;
  const maxInstant = end !== null ? end : moment.utc().startOf("day");

  const dateTimeFilter = Object.keys(filters).filter(
    (key) => filters[key].remove === false && key === "datetime"
  );
  const hasDateTimeInFilters = dateTimeFilter.length > 0;

  const extent = filter
    ? fromFilterString(filter)
    : {
        start,
        end,
      };

  const [instant, setInstant] = useState(moment.utc(extent.start));
  const [instantInput, setInstantInput] = useState(moment.utc(extent.start));
  const [period, setPeriod] = useState({
    start: moment.utc(extent.start),
    end: moment.utc(extent.end ? extent.end : extent.start),
  });
  const [periodInput, setPeriodInput] = useState({
    start: moment.utc(extent.start),
    end: moment.utc(extent.end ? extent.end : extent.start),
  });
  const [isInstant, setIsInstant] = useState(extent.end === null);

  const { t } = useTranslation();

  useEffect(() => {
    if (filter !== null) {
      const datetime = fromFilterString(filter);
      if (datetime.end === null) {
        setInstant(moment.utc(datetime.start));
      } else {
        setPeriod({
          start: moment.utc(datetime.start),
          end: moment.utc(datetime.end),
        });
      }
    }
  }, [filter]);

  const save = (event) => {
    event.preventDefault();
    event.stopPropagation();

    onChange(
      "datetime",
      isInstant ? formatDate(instant) : `${formatDate(period.start)}/${formatDate(period.end)}`
    );
  };

  const validInstant = useMemo(() => validateInstant(instantInput, min, max), [instantInput]);

  const validPeriod = useMemo(() => validatePeriod(periodInput, period, min, max), [periodInput]);

  const inputChangeInstant = useCallback((next) => {
    setInstantInput(next);
    if (validInstant) {
      setInstant(next);
      setIsInstant(true);
    }
  }, []);

  const inputChangePeriodStartNoRange = useCallback((next) => {
    if (moment.utc(start).isSame(moment.utc(end)) && moment.utc(next).isAfter(moment.utc(start))) {
      setPeriodInput((prev) => ({
        start: moment.utc(next).subtract(1, "day"),
        end: prev.end,
      }));
    } else {
      setPeriodInput((prev) => ({
        start: next,
        end: prev.end,
      }));
    }
  }, []);

  const inputChangePeriodStart = useCallback((next) => {
    setPeriodInput((prev) => ({
      start: next,
      end: prev.end,
    }));
    if (validPeriod.periodInputStart) {
      setPeriod((prevPeriod) => ({
        ...prevPeriod,
        start: next,
      }));
      setIsInstant(false);
    }
  }, []);

  const inputChangePeriodEnd = useCallback((next) => {
    setPeriodInput((prev) => ({
      start: prev.start,
      end: next,
    }));
    if (validPeriod.periodInputEnd) {
      setPeriod((prevPeriod) => ({
        ...prevPeriod,
        end: next,
      }));
      setIsInstant(false);
    }
  }, []);

  useEffect(() => {
    if (isInstant) {
      setInstantInput(instant);
      validateInstant(instantInput, min, max);
      errorInstant(instantInput);
    } else {
      setPeriodInput({
        start: period.start,
        end: period.end,
      });
      validatePeriod(periodInput, period, min, max);
    }
  }, [instant, period]);

  const hasRange = !moment.utc(start).isSame(moment.utc(end));

  // Highlights the whole start-end span across both calendars, exactly like the old
  // react-datetime-range-picker's own renderDay override did (see its source) — react-datetime
  // itself only knows about a single `value` per instance, so without this each calendar would
  // only ever mark its own end of the range as selected, with nothing shown in between.
  const renderDay = (dayProps, currentDate) => {
    const { className, ...rest } = dayProps;
    const classes = [
      className,
      currentDate.isBetween(periodInput.start, periodInput.end, "day") && "in-selecting-range",
      (currentDate.isSame(periodInput.start, "day") || currentDate.isSame(periodInput.end, "day")) &&
        "rdtActive",
    ]
      .filter(Boolean)
      .join(" ");

    return (
      <td {...rest} className={classes}>
        {currentDate.date()}
      </td>
    );
  };

  return (
    <Form onSubmit={save}>
      <p className="text-muted text-uppercase">{t("dateTimeUtc")}</p>{" "}
      <ButtonGroup className="mb-3">
        <Button
          color="primary"
          outline={isInstant}
          size="sm"
          className="py-0"
          onClick={() => setIsInstant(false)}
        >
          {t("period")}
        </Button>
        <Button
          color="primary"
          outline={!isInstant}
          size="sm"
          className="py-0"
          onClick={() => setIsInstant(true)}
        >
          {t("instant")}
        </Button>
      </ButtonGroup>
      <Row>
        {isInstant ? (
          <Col md="10">
            {!validInstant.instantInputValid && (
              <>
                <div style={{ marginBottom: "10px" }}>
                  {errorInstant(instantInput, min, max).map((error) => (
                    <FormText key={error}>{error}</FormText>
                  ))}
                </div>
              </>
            )}
            <Datetime
              className=""
              inputProps={{
                className: validInstant.instantInputValid
                  ? "form-control form-control-sm w-100 mb-3"
                  : "form-control form-control-sm w-100 mb-3 is-invalid",
                readOnly: moment.utc(start).isSame(moment.utc(end)),
                style: {
                  backgroundColor: "white",
                  cursor: "pointer",
                },
              }}
              timeFormat="HH:mm:ss"
              dateFormat="DD.MM.YYYY"
              utc
              value={instantInput}
              onChange={inputChangeInstant}
              onKeyPress={(event) => {
                if (event.key === "Enter" && validInstant) {
                  save(event);
                }
              }}
            />

            <Input size="sm" className="mb-3" disabled />
          </Col>
        ) : (
          <>
            <div
              style={{
                display: "flex",
                flexDirection: "column",
                marginBottom: "10px",
              }}
            >
              {!validPeriod.startValid && (
                <FormText style={{ marginLeft: "15px" }}>
                  {t("error.startDateOutOfRange")}{" "}
                </FormText>
              )}
              {!validPeriod.endValid && (
                <FormText style={{ marginLeft: "15px" }}>{t("error.endDateOutOfRange")} </FormText>
              )}
              {
                // prettier-ignore
                (!validPeriod.startLessEnd ||
                !validPeriod.endGreaterStart) && (
                  <FormText style={{ marginLeft: "15px" }}>
                    {t("error.startLessThanEnd")}
                  </FormText>
                )
              }
            </div>

            <div className="col-md-10">
              <Datetime
                inputProps={{
                  className:
                    validInstant && validPeriod.all
                      ? "form-control form-control-sm w-100 mb-3"
                      : "form-control form-control-sm w-100 mb-3 is-invalid",
                  ...(hasRange ? {} : { readOnly: true, style: readOnlyInputStyle }),
                }}
                timeFormat="HH:mm:ss"
                dateFormat="DD.MM.YYYY"
                utc
                value={periodInput.start}
                onChange={hasRange ? inputChangePeriodStart : inputChangePeriodStartNoRange}
                renderDay={renderDay}
              />
              <Datetime
                inputProps={{
                  className:
                    validInstant && validPeriod.all
                      ? "form-control form-control-sm w-100 mb-3"
                      : "form-control form-control-sm w-100 mb-3 is-invalid",
                  ...(hasRange ? {} : { readOnly: true, style: readOnlyInputStyle }),
                }}
                timeFormat="HH:mm:ss"
                dateFormat="DD.MM.YYYY"
                utc
                value={periodInput.end}
                onChange={inputChangePeriodEnd}
                renderDay={renderDay}
              />
            </div>
          </>
        )}
        {hasDateTimeInFilters ? (
          <Col md="2" className="d-flex align-items-end mb-3">
            <ButtonGroup>
              <Button
                color="primary"
                size="sm"
                style={{ minWidth: "40px" }}
                onClick={save}
                disabled={!validInstant.instantInputValid || !validPeriod.all}
              >
                {"\u2713"}
              </Button>
              <Button
                color="danger"
                size="sm"
                style={{ minWidth: "40px" }}
                onClick={deleteFilters("datetime")}
              >
                {"\u2716"}
              </Button>
            </ButtonGroup>
          </Col>
        ) : (
          <Col md="2" className="d-flex align-items-end mb-3">
            <Button
              color="primary"
              size="sm"
              onClick={save}
              disabled={!validInstant.instantInputValid || !validPeriod.all}
            >
              {t("add")}
            </Button>
          </Col>
        )}
      </Row>
      {min !== max && (
        <>
          <Row>
            <Col md="10">
              <Slider
                start={isInstant ? instant : period.start}
                end={isInstant ? instant : period.end}
                min={isInstant ? minInstant : min}
                max={isInstant ? maxInstant : max}
                isInstant={isInstant}
                onChange={isInstant ? setInstant : setPeriod}
              />
            </Col>
          </Row>
        </>
      )}
    </Form>
  );
};

TemporalFilter.displayName = "TemporalFilter";

TemporalFilter.propTypes = {
  start: PropTypes.number.isRequired,
  end: PropTypes.number,
  filter: PropTypes.string,
  onChange: PropTypes.func.isRequired,
  // eslint-disable-next-line react/forbid-prop-types
  filters: PropTypes.object.isRequired,
  deleteFilters: PropTypes.func.isRequired,
};

export default TemporalFilter;
