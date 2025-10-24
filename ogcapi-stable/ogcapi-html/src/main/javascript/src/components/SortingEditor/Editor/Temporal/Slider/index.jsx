import React, { useState, useEffect } from "react";
import PropTypes from "prop-types";
import moment from "moment";
import { Slider as ReactSlider, Rail, Handles, Tracks, Ticks } from "react-compound-slider";
import { differenceInMonths } from "date-fns";
import { scaleTime } from "d3-scale";
import { SliderRail, Handle, Track, Tick } from "./Parts";
import Header from "./Header";
import { formatTick } from "../util";

const sliderStyle = {
  position: "relative",
  width: "100%",
};

const Slider = ({ start, end, min, max, isInstant, onChange, showHeader }) => {
  const [updatedInstant, setUpdatedInstant] = useState(moment.utc(start).valueOf());
  const [updatedPeriod, setUpdatedPeriod] = useState([
    moment.utc(start).valueOf(),
    moment.utc(end).valueOf(),
  ]);

  const numSteps = 100;
  const range = max - min;
  const step = range / numSteps;

  const dateTicks = scaleTime()
    .domain([min, max])
    .ticks(8)
    .map((d) => +d);

  const onUpdateInstant = ([ms]) => {
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
        <ReactSlider
          mode={1}
          step={step}
          domain={[+min, +max]}
          rootStyle={sliderStyle}
          onUpdate={isInstant ? onUpdateInstant : onUpdatePeriod}
          values={isInstant ? [+updatedInstant] : [+updatedPeriod[0], +updatedPeriod[1]]}
        >
          <Rail>{({ getRailProps }) => <SliderRail getRailProps={getRailProps} />}</Rail>
          <Handles>
            {({ handles, getHandleProps }) => (
              <div>
                {handles.map((handle) => (
                  <Handle
                    key={handle.id}
                    handle={handle}
                    domain={[+min, +max]}
                    getHandleProps={getHandleProps}
                  />
                ))}
              </div>
            )}
          </Handles>
          <Tracks right={false} left={false}>
            {({ tracks, getTrackProps }) => (
              <div>
                {tracks.map(({ id, source, target }) => (
                  <Track key={id} source={source} target={target} getTrackProps={getTrackProps} />
                ))}
              </div>
            )}
          </Tracks>

          <Ticks values={dateTicks}>
            {({ ticks }) => (
              <div>
                {ticks.map((tick) => (
                  <Tick
                    key={tick.id}
                    tick={tick}
                    count={ticks.length}
                    format={formatTick(max, min)}
                  />
                ))}
              </div>
            )}
          </Ticks>
        </ReactSlider>
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

Slider.defaultProps = {
  showHeader: false,
};

export default Slider;
