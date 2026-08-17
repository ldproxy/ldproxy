import React, { useState, useEffect, useCallback, useMemo } from "react";
import Datetime from "react-datetime";
import "react-datetime/css/react-datetime.css";
import "./style.css";
import { Button, ButtonGroup, Form, Input, Row, Col, FormText } from "reactstrap";
import moment from "moment";
import { useTranslation } from "react-i18next";
import type { Filters } from "../../types";
import Slider from "./Slider";
import type { Period } from "./Slider";

import { validateInstant, validatePeriod, errorInstant } from "./util";

// react-datetime's own (hand-written, outdated) type definitions don't declare `onKeyPress`,
// even though the component passes extra props straight through to the underlying <input> at
// runtime - "submit on Enter" already relied on that before this conversion.
const DatetimeWithKeyPress = Datetime as unknown as React.ComponentType<
  React.ComponentProps<typeof Datetime> & { onKeyPress?: React.KeyboardEventHandler<HTMLInputElement> }
>;

// react-datetime-range-picker's own `input` prop never toggled react-datetime's `input` prop
// (which hides the text field entirely and shows the calendar permanently expanded) — it kept
// the field visible and just made it read-only-with-a-different-style. Replicated here directly
// via `inputProps`, since we render two plain <Datetime> instances instead of the old wrapper.
const readOnlyInputStyle = {
  cursor: "pointer",
  backgroundColor: "white",
  border: "1px solid #e2e2e2",
};

interface FilterInterval {
  start: string;
  end: string | null;
}

const fromFilterString = (filter: string): FilterInterval => {
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

export const toTimeLabel = (filter: string): string => {
  const datetime = fromFilterString(filter);

  if (!datetime.end) {
    return `datetime=${moment.utc(datetime.start).format("DD.MM.YY HH:mm:ss")}`;
  }
  return `datetime=${moment.utc(datetime.start).format("DD.MM.YY HH:mm:ss")} - ${moment
    .utc(datetime.end)
    .format("DD.MM.YY HH:mm:ss")}`;
};

const formatDate = (date: moment.MomentInput): string => {
  return moment.utc(date).format();
};

export interface TemporalFilterProps {
  start: number;
  end?: number | null;
  filter?: string | null;
  onChange: (field: string, value: string) => void;
  filters: Filters;
  deleteFilters: (field: string) => () => void;
}

const TemporalFilter = ({
  start,
  end = null,
  filter = null,
  onChange,
  filters,
  deleteFilters,
}: TemporalFilterProps) => {
  const min = start;
  const max = end;

  const minInstant = start;
  const maxInstant = end !== null ? end : moment.utc().startOf("day").valueOf();

  const dateTimeFilter = Object.keys(filters).filter(
    (key) => filters[key].remove === false && key === "datetime",
  );
  const hasDateTimeInFilters = dateTimeFilter.length > 0;

  const extent: FilterInterval | { start: number; end: number | null } = filter
    ? fromFilterString(filter)
    : {
        start,
        end,
      };

  const [instant, setInstant] = useState(moment.utc(extent.start));
  const [instantInput, setInstantInput] = useState(moment.utc(extent.start));
  const [period, setPeriod] = useState<Period>({
    start: moment.utc(extent.start),
    end: moment.utc(extent.end ? extent.end : extent.start),
  });
  const [periodInput, setPeriodInput] = useState<Period>({
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

  const save = (event: React.SyntheticEvent) => {
    event.preventDefault();
    event.stopPropagation();

    onChange(
      "datetime",
      isInstant ? formatDate(instant) : `${formatDate(period.start)}/${formatDate(period.end)}`,
    );
  };

  const validInstant = useMemo(
    () => validateInstant(instantInput, min, max ?? Infinity),
    [instantInput, min, max],
  );

  const validPeriod = useMemo(
    () => validatePeriod(periodInput, period, min, max ?? Infinity),
    [periodInput, period, min, max],
  );

  const inputChangeInstant = useCallback((next: moment.Moment | string) => {
    const nextMoment = moment.utc(next);
    setInstantInput(nextMoment);
    if (validInstant) {
      setInstant(nextMoment);
      setIsInstant(true);
    }
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, []);

  const inputChangePeriodStartNoRange = useCallback((next: moment.Moment | string) => {
    const nextMoment = moment.utc(next);
    if (moment.utc(start).isSame(moment.utc(end)) && nextMoment.isAfter(moment.utc(start))) {
      setPeriodInput((prev) => ({
        start: moment.utc(nextMoment).subtract(1, "day"),
        end: prev.end,
      }));
    } else {
      setPeriodInput((prev) => ({
        start: nextMoment,
        end: prev.end,
      }));
    }
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, []);

  const inputChangePeriodStart = useCallback((next: moment.Moment | string) => {
    const nextMoment = moment.utc(next);
    setPeriodInput((prev) => ({
      start: nextMoment,
      end: prev.end,
    }));
    if (validPeriod.periodInputStart) {
      setPeriod((prevPeriod) => ({
        ...prevPeriod,
        start: nextMoment,
      }));
      setIsInstant(false);
    }
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, []);

  const inputChangePeriodEnd = useCallback((next: moment.Moment | string) => {
    const nextMoment = moment.utc(next);
    setPeriodInput((prev) => ({
      start: prev.start,
      end: nextMoment,
    }));
    if (validPeriod.periodInputEnd) {
      setPeriod((prevPeriod) => ({
        ...prevPeriod,
        end: nextMoment,
      }));
      setIsInstant(false);
    }
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, []);

  useEffect(() => {
    if (isInstant) {
      setInstantInput(instant);
    } else {
      setPeriodInput({
        start: period.start,
        end: period.end,
      });
    }
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [instant, period]);

  const hasRange = !moment.utc(start).isSame(moment.utc(end));

  // Highlights the whole start-end span across both calendars, exactly like the old
  // react-datetime-range-picker's own renderDay override did (see its source) — react-datetime
  // itself only knows about a single `value` per instance, so without this each calendar would
  // only ever mark its own end of the range as selected, with nothing shown in between.
  const renderDay = (dayProps: React.HTMLProps<HTMLTableCellElement>, currentDate: moment.Moment) => {
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
                  {errorInstant(instantInput, min, max ?? Infinity).map((error) => (
                    <FormText key={error}>{error}</FormText>
                  ))}
                </div>
              </>
            )}
            <DatetimeWithKeyPress
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
              onKeyPress={(event: React.KeyboardEvent<HTMLInputElement>) => {
                if (event.key === "Enter" && validInstant) {
                  save(event);
                }
              }}
            />

            <Input bsSize="sm" className="mb-3" disabled />
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
                {"✓"}
              </Button>
              <Button
                color="danger"
                size="sm"
                style={{ minWidth: "40px" }}
                onClick={deleteFilters("datetime")}
              >
                {"✖"}
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
                max={isInstant ? maxInstant : (max ?? min)}
                isInstant={isInstant}
                onChange={(value) => {
                  if (isInstant) {
                    setInstant(value as moment.Moment);
                  } else {
                    setPeriod(value as Period);
                  }
                }}
              />
            </Col>
          </Row>
        </>
      )}
    </Form>
  );
};

TemporalFilter.displayName = "TemporalFilter";

export default TemporalFilter;
