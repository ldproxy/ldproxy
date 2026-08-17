export interface BoundsObject {
  minLng: number;
  minLat: number;
  maxLng: number;
  maxLat: number;
}

export type BoundsArray = [[number, number], [number, number]];

export const round = (value: number | string): number =>
  Math.round((parseFloat(String(value)) + Number.EPSILON) * 10000) / 10000;

export const roundBounds = (bounds: number[][] | undefined | null): BoundsArray | [] =>
  bounds && bounds.length > 0
    ? [
        [round(bounds[0][0]), round(bounds[0][1])],
        [round(bounds[1][0]), round(bounds[1][1])],
      ]
    : [];

const arrayEquals = (a: unknown, b: unknown, ignoreValues?: boolean): boolean => {
  return (
    Array.isArray(a) &&
    Array.isArray(b) &&
    a.length === b.length &&
    (Boolean(ignoreValues) || a.every((val, index) => val === b[index]))
  );
};

export const boundsArraysEqual = (a: BoundsArray, b: BoundsArray): boolean => {
  return arrayEquals(a, b, true) && a.every((val, index) => arrayEquals(val, b[index]));
};

export const boundsObjectEqualsArray = (
  boundsObject: BoundsObject,
  boundsArray: BoundsArray,
): boolean =>
  boundsObject.minLng === boundsArray[0][0] &&
  boundsObject.minLat === boundsArray[0][1] &&
  boundsObject.maxLng === boundsArray[1][0] &&
  boundsObject.maxLat === boundsArray[1][1];

export const boundsAsObject = (boundsArray: BoundsArray): BoundsObject => ({
  minLng: boundsArray[0][0],
  minLat: boundsArray[0][1],
  maxLng: boundsArray[1][0],
  maxLat: boundsArray[1][1],
});

export const boundsAsArray = (boundsObject: BoundsObject): BoundsArray => [
  [boundsObject.minLng, boundsObject.minLat],
  [boundsObject.maxLng, boundsObject.maxLat],
];

export const boundsAsString = (boundsObject: BoundsObject): string => {
  const { minLng, minLat, maxLng, maxLat } = boundsObject;

  return `${minLng.toFixed(4)},${minLat.toFixed(4)},${maxLng.toFixed(4)},${maxLat.toFixed(4)}`;
};

const testLng = (lng: number): boolean => {
  return lng >= -180 && lng <= 180;
};

const testLat = (lat: number): boolean => {
  return lat >= -90 && lat <= 90;
};

const testMinMax = (min: number, max: number): boolean => {
  return min <= max;
};

export interface BoundsValidation {
  all: boolean;
  minLng: boolean;
  minLat: boolean;
  maxLng: boolean;
  maxLat: boolean;
  minMaxLng: boolean;
  minMaxLat: boolean;
}

export const validateBounds = (boundsObject: BoundsObject): BoundsValidation => {
  const { minLng, minLat, maxLng, maxLat } = boundsObject;
  const isLngMinValid = testLng(minLng);
  const isLatMinValid = testLat(minLat);
  const isLngMaxValid = testLng(maxLng);
  const isLatMaxValid = testLat(maxLat);
  const isLngMinMaxValid = testMinMax(minLng, maxLng);
  const isLatMinMaxValid = testMinMax(minLat, maxLat);

  return {
    all:
      isLngMinValid &&
      isLatMinValid &&
      isLngMaxValid &&
      isLatMaxValid &&
      isLngMinMaxValid &&
      isLatMinMaxValid,
    minLng: isLngMinValid,
    minLat: isLatMinValid,
    maxLng: isLngMaxValid,
    maxLat: isLatMaxValid,
    minMaxLng: isLngMinMaxValid,
    minMaxLat: isLatMinMaxValid,
  };
};

export const areBoundsValid = (boundsObject: BoundsObject): boolean =>
  validateBounds(boundsObject).all;
