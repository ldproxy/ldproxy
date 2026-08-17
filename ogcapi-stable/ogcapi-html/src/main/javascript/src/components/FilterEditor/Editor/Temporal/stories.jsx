import moment from "moment";
import Slider from "./Slider";

export default {
  title: "@ogcapi/html/FilterEditor/Editor/Temporal",
  component: Slider,
};

const instantArgs = (min, max) => ({
  min: moment.utc(min).valueOf(),
  max: moment.utc(max).valueOf(),
  start: moment.utc(min),
  end: moment.utc(min),
  onChange: () => {},
  isInstant: true,
  showHeader: true,
});

const periodArgs = (min, max, end) => ({
  min: moment.utc(min).valueOf(),
  max: moment.utc(max).valueOf(),
  start: moment.utc(min),
  end: moment.utc(end),
  onChange: () => {},
  isInstant: false,
  showHeader: true,
});

export const InstantMoreThan7Years = {
  args: instantArgs("01 Jan 2019 00:00:00", "01 Jan 2028 00:57:00"),
};
export const InstantMoreThan3Years = {
  args: instantArgs("01 Jan 2019 00:00:00", "01 Jan 2024 00:57:00"),
};
export const InstantMoreThan7Months = {
  args: instantArgs("01 Jan 2019 00:00:00", "01 Dec 2019 00:57:00"),
};
export const InstantMoreThan24h = {
  args: instantArgs("01 Jan 2019 00:00:00", "08 Jan 2019 00:00:00"),
};
export const InstantLessThan24h = {
  args: instantArgs("01 Jan 2019 00:00:00", "01 Jan 2019 23:00:00"),
};

export const PeriodMoreThan7Years = {
  args: periodArgs("01 Jan 2019 00:00:00", "31 Dec 2028 00:00:00", "31 Dec 2028 00:00:00"),
};
export const PeriodMoreThan3Years = {
  args: periodArgs("01 Jan 2019 00:00:00", "01 Jan 2024 00:00:00", "01 Jan 2024 00:00:00"),
};
export const PeriodMoreThan7Months = {
  args: periodArgs("01 Jan 2019 00:00:00", "01 Dec 2019 00:00:00", "01 Dec 2019 00:00:00"),
};
export const PeriodMoreThan24h = {
  args: periodArgs("01 Jan 2019 00:00:00", "07 Jun 2019 00:00:00", "07 Jun 2019 00:00:00"),
};
export const PeriodLessThan24h = {
  args: periodArgs("01 Jan 2019 00:00:00", "01 Jan 2019 23:00:00", "01 Jan 2019 23:00:00"),
};
