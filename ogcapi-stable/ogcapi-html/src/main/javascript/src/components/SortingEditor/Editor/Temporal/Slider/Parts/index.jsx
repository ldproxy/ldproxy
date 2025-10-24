// @flow weak

import React from "react";
import PropTypes from "prop-types";

// *******************************************************
// RAIL
// *******************************************************
const railOuterStyle = {
  position: "absolute",
  width: "100%",
  height: 40,
  transform: "translate(0%, -50%)",
  cursor: "pointer",
  border: "1px solid white",
};

const railInnerStyle = {
  position: "absolute",
  width: "100%",
  height: 8,
  transform: "translate(0%, -50%)",
  borderRadius: 4,
  pointerEvents: "none",
  backgroundColor: "rgb(155,155,155)",
};

export function SliderRail({ getRailProps }) {
  return (
    <>
      <div style={railOuterStyle} {...getRailProps()} />
      <div style={railInnerStyle} />
    </>
  );
}

SliderRail.propTypes = {
  getRailProps: PropTypes.func.isRequired,
};

// *******************************************************
// HANDLE COMPONENT
// *******************************************************
export function Handle({ domain: [min, max], handle: { id, value, percent }, getHandleProps }) {
  return (
    <>
      <div
        style={{
          left: `${percent}%`,
          position: "absolute",
          transform: "translate(-50%, -50%)",
          WebkitTapHighlightColor: "rgba(0,0,0,0)",
          zIndex: 5,
          width: 24,
          height: 42,
          cursor: "pointer",
          // border: "1px solid white",
          backgroundColor: "none",
        }}
        {...getHandleProps(id)}
      />
      <div
        className="bg-primary"
        aria-label="Slider handle"
        role="slider"
        aria-valuemin={min}
        aria-valuemax={max}
        aria-valuenow={value}
        style={{
          left: `${percent}%`,
          position: "absolute",
          transform: "translate(-50%, -50%)",
          zIndex: 2,
          width: 20,
          height: 20,
          borderRadius: "50%",
          boxShadow: "1px 1px 1px 0px rgba(0, 0, 0, 0.3)",
          //  backgroundColor: disabled ? "#666" : "#ff0000",
        }}
      />
    </>
  );
}

Handle.propTypes = {
  domain: PropTypes.arrayOf.isRequired,
  handle: PropTypes.shape({
    id: PropTypes.string.isRequired,
    value: PropTypes.number.isRequired,
    percent: PropTypes.number.isRequired,
  }).isRequired,
  getHandleProps: PropTypes.func.isRequired,
  disabled: PropTypes.bool,
};

Handle.defaultProps = {
  disabled: false,
};

// *******************************************************
// TRACK COMPONENT
// *******************************************************
export function Track({ source, target, getTrackProps }) {
  return (
    <div
      className="bg-primary"
      style={{
        position: "absolute",
        transform: "translate(0%, -50%)",
        height: 8,
        zIndex: 1,
        // backgroundColor: disabled ? "#999" : "#444",
        borderRadius: 4,
        cursor: "pointer",
        left: `${source.percent}%`,
        width: `${target.percent - source.percent}%`,
      }}
      {...getTrackProps()}
    />
  );
}

Track.propTypes = {
  source: PropTypes.shape({
    id: PropTypes.string.isRequired,
    value: PropTypes.number.isRequired,
    percent: PropTypes.number.isRequired,
  }).isRequired,
  target: PropTypes.shape({
    id: PropTypes.string.isRequired,
    value: PropTypes.number.isRequired,
    percent: PropTypes.number.isRequired,
  }).isRequired,
  getTrackProps: PropTypes.func.isRequired,
  disabled: PropTypes.bool,
};

Track.defaultProps = {
  disabled: false,
};

// *******************************************************
// TICK COMPONENT
// *******************************************************
export function Tick({ tick, count, format }) {
  return (
    <div>
      <div
        style={{
          position: "absolute",
          marginTop: 14,
          width: 1,
          height: 5,
          backgroundColor: "rgb(200,200,200)",
          left: `${tick.percent}%`,
        }}
      />
      <div
        style={{
          position: "absolute",
          marginTop: 22,
          fontSize: 10,
          textAlign: "center",
          fontFamily: "Arial, san-serif",
          marginLeft: `${-(100 / count) / 2}%`,
          width: `${100 / count}%`,
          left: `${tick.percent}%`,
        }}
      >
        {format(tick.value)}
      </div>
    </div>
  );
}

Tick.propTypes = {
  tick: PropTypes.shape({
    id: PropTypes.string.isRequired,
    value: PropTypes.number.isRequired,
    percent: PropTypes.number.isRequired,
  }).isRequired,
  count: PropTypes.number.isRequired,
  format: PropTypes.func.isRequired,
};

/*
Tick.defaultProps = {
  format: (d) => d,
};
*/
