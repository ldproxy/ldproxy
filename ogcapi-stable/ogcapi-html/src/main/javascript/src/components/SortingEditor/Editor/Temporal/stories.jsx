import React from "react";
import { storiesOf } from "@storybook/react";
import moment from "moment";
import Slider from "./Slider";

const Instant = (args) => {
  return (
    <>
      <Slider {...args} />
    </>
  );
};

const Period = (args) => {
  return (
    <>
      <Slider {...args} />
    </>
  );
};

// Stories for SliderInstant:

storiesOf("@ogcapi/html/FilterEditor/Editor/Temporal/Instant/MoreThan7Years", module).add(
  "Default",
  () => (
    <Instant
      min={moment.utc("01 Jan 2019 00:00:00").valueOf()}
      max={moment.utc("01 Jan 2028 00:57:00").valueOf()}
      start={moment.utc("01 Jan 2019 00:00:00")}
      end={moment.utc("01 Jan 2019 00:00:00")}
      onChange={() => {}}
      isInstant
      showHeader
    />
  )
);

storiesOf("@ogcapi/html/FilterEditor/Editor/Temporal/Instant/MoreThan3Years", module).add(
  "Default",
  () => (
    <Instant
      min={moment.utc("01 Jan 2019 00:00:00").valueOf()}
      max={moment.utc("01 Jan 2024 00:57:00").valueOf()}
      start={moment.utc("01 Jan 2019 00:00:00")}
      end={moment.utc("01 Jan 2019 00:00:00")}
      onChange={() => {}}
      isInstant
      showHeader
    />
  )
);

storiesOf("@ogcapi/html/FilterEditor/Editor/Temporal/Instant/MoreThan7Months", module).add(
  "Default",
  () => (
    <Instant
      min={moment.utc("01 Jan 2019 00:00:00").valueOf()}
      max={moment.utc("01 Dec 2019 00:57:00").valueOf()}
      start={moment.utc("01 Jan 2019 00:00:00")}
      end={moment.utc("01 Jan 2019 00:00:00")}
      onChange={() => {}}
      isInstant
      showHeader
    />
  )
);

storiesOf("@ogcapi/html/FilterEditor/Editor/Temporal/Instant/MoreThan24h", module).add(
  "Default",
  () => (
    <Instant
      min={moment.utc("01 Jan 2019 00:00:00").valueOf()}
      max={moment.utc("08 Jan 2019 00:00:00").valueOf()}
      start={moment.utc("01 Jan 2019 00:00:00")}
      end={moment.utc("01 Jan 2019 00:00:00")}
      onChange={() => {}}
      isInstant
      showHeader
    />
  )
);

storiesOf("@ogcapi/html/FilterEditor/Editor/Temporal/Instant/LessThan24h", module).add(
  "Default",
  () => (
    <Instant
      min={moment.utc("01 Jan 2019 00:00:00").valueOf()}
      max={moment.utc("01 Jan 2019 23:00:00").valueOf()}
      start={moment.utc("01 Jan 2019 00:00:00")}
      end={moment.utc("01 Jan 2019 00:00:00")}
      onChange={() => {}}
      isInstant
      showHeader
    />
  )
);

// Stories for SliderPeriod:

storiesOf("@ogcapi/html/FilterEditor/Editor/Temporal/Period/MoreThan7Years", module).add(
  "Default",
  () => (
    <Period
      min={moment.utc("01 Jan 2019 00:00:00").valueOf()}
      max={moment.utc("31 Dec 2028 00:00:00").valueOf()}
      start={moment.utc("01 Jan 2019 00:00:00")}
      end={moment.utc("31 Dec 2028 00:00:00")}
      onChange={() => {}}
      isInstant={false}
      showHeader
    />
  )
);

storiesOf("@ogcapi/html/FilterEditor/Editor/Temporal/Period/MoreThan3Years", module).add(
  "Default",
  () => (
    <Period
      min={moment.utc("01 Jan 2019 00:00:00").valueOf()}
      max={moment.utc("01 Jan 2024 00:00:00").valueOf()}
      start={moment.utc("01 Jan 2019 00:00:00")}
      end={moment.utc("01 Jan 2024 00:00:00")}
      onChange={() => {}}
      isInstant={false}
      showHeader
    />
  )
);

storiesOf("@ogcapi/html/FilterEditor/Editor/Temporal/Period/MoreThan7Months", module).add(
  "Default",
  () => (
    <Period
      min={moment.utc("01 Jan 2019 00:00:00").valueOf()}
      max={moment.utc("01 Dec 2019 00:00:00").valueOf()}
      start={moment.utc("01 Jan 2019 00:00:00")}
      end={moment.utc("01 Dec 2019 00:00:00")}
      onChange={() => {}}
      isInstant={false}
      showHeader
    />
  )
);

storiesOf("@ogcapi/html/FilterEditor/Editor/Temporal/Period/MoreThan24h", module).add(
  "Default",
  () => (
    <Period
      min={moment.utc("01 Jan 2019 00:00:00").valueOf()}
      max={moment.utc("07 Jun 2019 00:00:00").valueOf()}
      start={moment.utc("01 Jan 2019 00:00:00")}
      end={moment.utc("07 Jun 2019 00:00:00")}
      onChange={() => {}}
      isInstant={false}
      showHeader
    />
  )
);

storiesOf("@ogcapi/html/FilterEditor/Editor/Temporal/Period/LessThan24h", module).add(
  "Default",
  () => (
    <Period
      min={moment.utc("01 Jan 2019 00:00:00").valueOf()}
      max={moment.utc("01 Jan 2019 23:00:00").valueOf()}
      start={moment.utc("01 Jan 2019 00:00:00")}
      end={moment.utc("01 Jan 2019 23:00:00")}
      onChange={() => {}}
      isInstant={false}
      showHeader
    />
  )
);
