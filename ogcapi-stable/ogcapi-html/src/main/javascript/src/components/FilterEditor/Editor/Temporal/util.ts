import moment from "moment";
import { differenceInYears, differenceInMonths, differenceInHours } from "date-fns";

export interface Period {
  start: moment.MomentInput;
  end: moment.MomentInput;
}

// Accepts whatever moment.utc() itself accepts (Moment | string | number | Date | ...). The
// "DD.MM.YYYY HH:mm:ss" format string below only applies when the input is a plain string -
// moment.js ignores it and clones the value as-is when given an already-parsed Moment/Date,
// which is how these are actually called from Temporal/index.tsx (always with live `moment()`
// instances, never formatted strings).
export const errorInstant = (
  instantInput: moment.MomentInput,
  min: moment.MomentInput,
  max: moment.MomentInput,
): string[] => {
  const parsedDate = moment.utc(instantInput, "DD.MM.YYYY HH:mm:ss", true);
  const errors: string[] = [];

  if (!parsedDate.isValid()) {
    errors.push("Invalid date format.");
  } else {
    if (!parsedDate.isSameOrAfter(moment.utc(min))) {
      errors.push("Date is before the minimum date.");
    }
    if (!parsedDate.isSameOrBefore(moment.utc(max))) {
      errors.push("Date is after the maximum date.");
    }
  }

  return errors;
};

const testFunctionInstant = (
  instantInput: moment.MomentInput,
  min: moment.MomentInput,
  max: moment.MomentInput,
): boolean => {
  errorInstant(instantInput, min, max);
  const parsedDate = moment.utc(instantInput, "DD.MM.YYYY HH:mm:ss", true);
  if (
    parsedDate.isValid() &&
    parsedDate.isSameOrAfter(moment.utc(min)) &&
    parsedDate.isSameOrBefore(moment.utc(max))
  ) {
    return true;
  }
  return false;
};

const testStart = (periodInput: Period, min: moment.MomentInput, max: moment.MomentInput): boolean => {
  const parsedDate = moment.utc(periodInput.start, "DD.MM.YYYY HH:mm:ss", true);
  if (
    parsedDate.isValid() &&
    parsedDate.isSameOrAfter(moment.utc(min)) &&
    parsedDate.isSameOrBefore(moment.utc(max))
  ) {
    return true;
  }
  return false;
};

const testStartLessEnd = (periodInput: Period, period: Period): boolean => {
  const parsedDate = moment.utc(periodInput.start, "DD.MM.YYYY HH:mm:ss", true);
  if (parsedDate.isSameOrBefore(moment.utc(period.end))) {
    return true;
  }
  return false;
};

const testEnd = (periodInput: Period, max: moment.MomentInput, min: moment.MomentInput): boolean => {
  const parsedDate = moment.utc(periodInput.end, "DD.MM.YYYY HH:mm:ss", true);
  if (
    parsedDate.isValid() &&
    parsedDate.isSameOrBefore(moment.utc(max)) &&
    parsedDate.isSameOrAfter(moment.utc(min))
  ) {
    return true;
  }
  return false;
};

const testEndGreaterStart = (periodInput: Period, period: Period): boolean => {
  const parsedDate = moment.utc(periodInput.end, "DD.MM.YYYY HH:mm:ss", true);
  if (parsedDate.isSameOrAfter(moment.utc(period.start))) {
    return true;
  }
  return false;
};

export const validateInstant = (
  instantInput: moment.MomentInput,
  min: moment.MomentInput,
  max: moment.MomentInput,
): { instantInputValid: boolean } => {
  const instantValid = testFunctionInstant(instantInput, min, max);
  return {
    instantInputValid: instantValid,
  };
};

export interface PeriodValidation {
  startValid: boolean;
  startLessEnd: boolean;
  endValid: boolean;
  endGreaterStart: boolean;
  all: boolean;
  periodInputStart: boolean;
  periodInputEnd: boolean;
}

export const validatePeriod = (
  periodInput: Period,
  period: Period,
  min: moment.MomentInput,
  max: moment.MomentInput,
): PeriodValidation => {
  const startValid = testStart(periodInput, min, max);
  const startLessEnd = testStartLessEnd(periodInput, period);
  const endValid = testEnd(periodInput, max, min);
  const endGreaterStart = testEndGreaterStart(periodInput, period);

  return {
    startValid,
    startLessEnd,
    endValid,
    endGreaterStart,

    all: startValid && startLessEnd && endValid && endGreaterStart,
    periodInputStart: startValid && startLessEnd,
    periodInputEnd: endValid && endGreaterStart,
  };
};

export const isPeriodValid = (
  periodInput: Period,
  period: Period,
  min: moment.MomentInput,
  max: moment.MomentInput,
): boolean => validatePeriod(periodInput, period, min, max).all;

export const formatTick =
  (max: number | Date, min: number | Date) =>
  (ms: number): string | undefined => {
    let dateFormat: string | undefined;
    if (differenceInYears(max, min) > 7) {
      dateFormat = moment.utc(ms).format("yyyy");
    } else if (differenceInYears(max, min) > 3) {
      dateFormat = moment.utc(ms).format("MMM yyyy");
    } else if (differenceInMonths(max, min) > 7) {
      dateFormat = moment.utc(ms).format("MMM");
    } else if (differenceInHours(max, min) > 24) {
      dateFormat = moment.utc(ms).format("DD MMM");
    } else if (differenceInHours(max, min) < 24) {
      dateFormat = moment.utc(ms).format("HH:mm:ss");
    }
    return dateFormat;
  };
