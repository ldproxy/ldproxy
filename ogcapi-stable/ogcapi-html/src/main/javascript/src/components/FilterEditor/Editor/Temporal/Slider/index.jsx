import React, { useState, useEffect } from "react";
import PropTypes from "prop-types";
import moment from "moment";
import RcSlider from "rc-slider";
import "rc-slider/assets/index.css";
import { differenceInMonths } from "date-fns";
import { scaleTime } from "d3-scale";
import Header from "./Header";
import { formatTick } from "../util";

// Matches the look of the old react-compound-slider-based Rail/Handle/Track (see git history of
// ./Parts) as closely as possible: plain gray rail, blue round handles, no rc-slider chrome.
// Colors are hardcoded (Bootstrap 4's $primary) rather than left to the `bg-primary` class,
// since class vs. class specificity ties would otherwise depend on unpredictable CSS load order
// between rc-slider's stylesheet and bootstrap.css.
const RAIL_STYLE = { height: 8, borderRadius: 4, backgroundColor: "rgb(155, 155, 155)" };
const TRACK_STYLE = { height: 8, borderRadius: 4, backgroundColor: "#007bff" };
const HANDLE_STYLE = {
  width: 20,
  height: 20,
  marginTop: -6,
  borderRadius: "50%",
  border: "none",
  backgroundColor: "#007bff",
  boxShadow: "1px 1px 1px 0px rgba(0, 0, 0, 0.3)",
  opacity: 1,
};

const renderHandle = (origin) =>
  React.cloneElement(origin, {
    style: { ...origin.props.style, ...HANDLE_STYLE },
  });

const Slider = ({ start, end, min, max, isInstant, onChange, showHeader = false }) => {
  const [updatedInstant, setUpdatedInstant] = useState(moment.utc(start).valueOf());
  const [updatedPeriod, setUpdatedPeriod] = useState([
    moment.utc(start).valueOf(),
    moment.utc(end).valueOf(),
  ]);

  const numSteps = 100;
  const range = max - min;
  const step = range / numSteps;

  const marks = scaleTime()
    .domain([min, max])
    .ticks(8)
    .reduce((acc, d) => ({ ...acc, [+d]: formatTick(max, min)(+d) }), {});

  const onUpdateInstant = (ms) => {
    setUpdatedInstant(ms);
    onChange(moment.utc(ms));
  };

  const onUpdatePeriod = (updatedValues) => {
    setUpdatedPeriod([updatedValues[0], updatedValues[1]]);
    onChange({
      start: moment.utc(updatedValues[0]),
      end: moment.utc(updatedValues[1]),
    });
  };

  useEffect(() => {
    if (isInstant) {
      const nextInstant = moment.utc(start).valueOf();
      const steps = [...Array(numSteps + 1).keys()].map((i) => min + i * step);
      const closestStep = steps.reduce((prev, curr) =>
        Math.abs(curr - nextInstant) < Math.abs(prev - nextInstant) ? curr : prev
      );
      setUpdatedInstant(closestStep);
    } else {
      const nextStart = moment.utc(start).valueOf();
      const nextEnd = moment.utc(end).valueOf();
      const steps = [...Array(numSteps + 1).keys()].map((i) => min + i * step);
      const closestStepStart = steps.reduce((prev, curr) =>
        Math.abs(curr - nextStart) < Math.abs(prev - nextStart) ? curr : prev
      );
      const closestStepEnd = steps.reduce((prev, curr) =>
        Math.abs(curr - nextEnd) < Math.abs(prev - nextEnd) ? curr : prev
      );
      setUpdatedPeriod([closestStepStart, closestStepEnd]);
    }
  }, [start, end, isInstant]);

  return (
    <div>
      {showHeader &&
        (isInstant ? (
          <Header start={+updatedInstant} hideTime={differenceInMonths(max, min) > 1} />
        ) : (
          <Header
            start={+updatedPeriod[0]}
            end={+updatedPeriod[1]}
            hideTime={differenceInMonths(max, min) > 1}
          />
        ))}
      <div style={{ margin: "10px", height: 120 }}>
        {isInstant ? (
          <RcSlider
            min={+min}
            max={+max}
            step={step}
            marks={marks}
            value={+updatedInstant}
            onChange={onUpdateInstant}
            handleRender={renderHandle}
            styles={{ rail: RAIL_STYLE, track: { backgroundColor: "transparent" } }}
          />
        ) : (
          <RcSlider
            range
            allowCross={false}
            min={+min}
            max={+max}
            step={step}
            marks={marks}
            value={[+updatedPeriod[0], +updatedPeriod[1]]}
            onChange={onUpdatePeriod}
            handleRender={renderHandle}
            styles={{ rail: RAIL_STYLE, track: TRACK_STYLE }}
          />
        )}
      </div>
    </div>
  );
};

Slider.propTypes = {
  min: PropTypes.number.isRequired,
  max: PropTypes.number.isRequired,
  start: PropTypes.instanceOf(moment).isRequired,
  end: PropTypes.instanceOf(moment).isRequired,
  isInstant: PropTypes.bool.isRequired,
  onChange: PropTypes.func.isRequired,
  showHeader: PropTypes.bool,
};

export default Slider;
