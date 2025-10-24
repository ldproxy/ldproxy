import moment from "moment";
import { differenceInYears, differenceInMonths, differenceInHours } from "date-fns";

export const errorInstant = (instantInput, min, max) => {
  const parsedDate = moment.utc(instantInput, "DD.MM.YYYY HH:mm:ss", true);
  const errors = [];

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

const testFunctionInstant = (instantInput, min, max) => {
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

const testStart = (periodInput, min, max) => {
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

const testStartLessEnd = (periodInput, period) => {
  const parsedDate = moment.utc(periodInput.start, "DD.MM.YYYY HH:mm:ss", true);
  if (parsedDate.isSameOrBefore(moment.utc(period.end))) {
    return true;
  }
  return false;
};

const testEnd = (periodInput, max, min) => {
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

const testEndGreaterStart = (periodInput, period) => {
  const parsedDate = moment.utc(periodInput.end, "DD.MM.YYYY HH:mm:ss", true);
  if (parsedDate.isSameOrAfter(moment.utc(period.start))) {
    return true;
  }
  return false;
};

export const validateInstant = (instantInput, min, max) => {
  const instantValid = testFunctionInstant(instantInput, min, max);
  return {
    instantInputValid: instantValid,
  };
};

export const validatePeriod = (periodInput, period, min, max) => {
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

export const isPeriodValid = (periodInput, period, min, max) =>
  validatePeriod(periodInput, period, min, max).all;

export const formatTick = (max, min) => (ms) => {
  let dateFormat;
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
