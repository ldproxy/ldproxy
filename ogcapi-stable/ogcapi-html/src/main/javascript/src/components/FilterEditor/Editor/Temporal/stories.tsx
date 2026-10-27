import moment from "moment";
import type { Meta, StoryObj } from "@storybook/react-vite";
import Slider from "./Slider";

const meta: Meta<typeof Slider> = {
  title: "@ogcapi/html/FilterEditor/Editor/Temporal",
  component: Slider,
};
export default meta;

type Story = StoryObj<typeof Slider>;

const instantArgs = (min: string, max: string) => ({
  min: moment.utc(min).valueOf(),
  max: moment.utc(max).valueOf(),
  start: moment.utc(min),
  end: moment.utc(min),
  onChange: () => {},
  isInstant: true,
  showHeader: true,
});

const periodArgs = (min: string, max: string, end: string) => ({
  min: moment.utc(min).valueOf(),
  max: moment.utc(max).valueOf(),
  start: moment.utc(min),
  end: moment.utc(end),
  onChange: () => {},
  isInstant: false,
  showHeader: true,
});

export const InstantMoreThan7Years: Story = {
  args: instantArgs("01 Jan 2019 00:00:00", "01 Jan 2028 00:57:00"),
};
export const InstantMoreThan3Years: Story = {
  args: instantArgs("01 Jan 2019 00:00:00", "01 Jan 2024 00:57:00"),
};
export const InstantMoreThan7Months: Story = {
  args: instantArgs("01 Jan 2019 00:00:00", "01 Dec 2019 00:57:00"),
};
export const InstantMoreThan24h: Story = {
  args: instantArgs("01 Jan 2019 00:00:00", "08 Jan 2019 00:00:00"),
};
export const InstantLessThan24h: Story = {
  args: instantArgs("01 Jan 2019 00:00:00", "01 Jan 2019 23:00:00"),
};

export const PeriodMoreThan7Years: Story = {
  args: periodArgs("01 Jan 2019 00:00:00", "31 Dec 2028 00:00:00", "31 Dec 2028 00:00:00"),
};
export const PeriodMoreThan3Years: Story = {
  args: periodArgs("01 Jan 2019 00:00:00", "01 Jan 2024 00:00:00", "01 Jan 2024 00:00:00"),
};
export const PeriodMoreThan7Months: Story = {
  args: periodArgs("01 Jan 2019 00:00:00", "01 Dec 2019 00:00:00", "01 Dec 2019 00:00:00"),
};
export const PeriodMoreThan24h: Story = {
  args: periodArgs("01 Jan 2019 00:00:00", "07 Jun 2019 00:00:00", "07 Jun 2019 00:00:00"),
};
export const PeriodLessThan24h: Story = {
  args: periodArgs("01 Jan 2019 00:00:00", "01 Jan 2019 23:00:00", "01 Jan 2019 23:00:00"),
};
