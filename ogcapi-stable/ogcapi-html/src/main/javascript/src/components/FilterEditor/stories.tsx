import React from "react";
import Editor from "./Editor";
import EditorHeader from "./Editor/Header";

export default {
  title: "@ogcapi/html/FilterEditor",
};

const noopDeleteFilters = () => () => {};

// Story fixtures deliberately exercise malformed/loose shapes (e.g. `isOpen: "true"` as a
// string, `start: {}` instead of a number) to visually probe how the components degrade -
// typing `args` any looser than `any` would make most of the fixtures below rejected outright.
// eslint-disable-next-line @typescript-eslint/no-explicit-any
const Template = (args: any) => (
  <>
    <EditorHeader {...args} />
    <Editor {...args} deleteFilters={noopDeleteFilters} />
  </>
);

// eslint-disable-next-line @typescript-eslint/no-explicit-any
const Header = (args: any) => (
  <>
    <EditorHeader {...args} />
  </>
);

const allFields = {
  firstName: "Vorname",
  lastName: "Nachname",
  age: "Alter",
  alive: "Lebendig",
  accountBalance: "Kontostand",
};

export const Plain = {
  render: Template,
  args: {
    fields: allFields,
    isOpen: "true",
    isEnabled: "true",
    filters: {},
    spatial: [
      [5.719412969894958, 50.31135979170666],
      [9.46927842749998, 53.15055217399161],
    ],
    code: { age: "567" },
    integerKeys: ["accountBalance"],
    booleanProperty: ["alive"],
    titleForFilter: allFields,
    start: 1666375200000,
    end: 1666375200000,
    temporal: { start: 1666375200000, end: 1666375200000 },
  },
};

export const OneFilter = {
  render: Template,
  args: {
    fields: allFields,
    isOpen: "true",
    isEnabled: "true",
    filters: { firstName: { value: "Hans", add: false, remove: false } },
    spatial: [],
    code: { age: "567" },
    integerKeys: ["accountBalance"],
    booleanProperty: ["alive"],
    titleForFilter: allFields,
    start: {},
    end: {},
    temporal: {},
  },
};

export const AllFieldsInFilters = {
  render: Template,
  args: {
    fields: allFields,
    isOpen: "true",
    isEnabled: "true",
    filters: {
      firstName: { value: "Hans", add: false, remove: false },
      accountBalance: { value: 1000, add: false, remove: false },
      lastName: { value: "Zahnen", add: false, remove: false },
      age: { value: 27, add: false, remove: false },
      alive: { value: true, add: false, remove: false },
    },
    changedValue: { firstName: { filterKey: "firstName", value: "Han" } },
    spatial: [],
    code: { age: "567" },
    integerKeys: ["accountBalance"],
    booleanProperty: ["alive"],
    titleForFilter: allFields,
    start: {},
    end: {},
    temporal: {},
  },
};

export const OnlyBbox = {
  render: Template,
  args: {
    fields: {},
    isOpen: "true",
    isEnabled: "true",
    filters: {},
    spatial: [
      [5.719412969894958, 50.31135979170666],
      [9.46927842749998, 53.15055217399161],
    ],
    code: { age: "567" },
    integerKeys: ["accountBalance"],
    booleanProperty: ["alive"],
    titleForFilter: allFields,
    start: {},
    end: {},
    temporal: {},
  },
};

export const OnlyTemporal = {
  render: Template,
  args: {
    fields: {},
    isOpen: "true",
    isEnabled: "true",
    filters: {},
    spatial: [],
    code: { age: "567" },
    integerKeys: ["accountBalance"],
    booleanProperty: ["alive"],
    titleForFilter: {},
    start: 1666375200000,
    end: 1666375200000,
    temporal: { start: 1666375200000, end: 1666375200000 },
  },
};

export const HeaderOnlyWithoutFilters = {
  render: Header,
  args: {
    fields: {},
    isOpen: "true",
    isEnabled: "true",
    filters: {},
    spatial: [],
    code: { age: "567" },
    integerKeys: ["accountBalance"],
    booleanProperty: ["alive"],
    titleForFilter: {},
    start: 1666375200000,
    end: 1666375200000,
    temporal: { start: 1666375200000 },
  },
};

export const HeaderOnlyWithOneFilter = {
  render: Header,
  args: {
    fields: { firstName: "Vorname" },
    isOpen: "true",
    isEnabled: "true",
    filters: { firstName: { value: "Hans", add: false, remove: false } },
    spatial: [],
    code: { age: "567" },
    integerKeys: ["accountBalance"],
    booleanProperty: ["alive"],
    titleForFilter: { firstName: "Vorname" },
    start: 1666375200000,
    end: 1666375200000,
    temporal: {},
  },
};

export const HeaderOnlyWithTwoFilters = {
  render: Header,
  args: {
    fields: {
      firstName: "Vorname",
      lastName: "Nachname",
      age: "Alter",
      alive: "Lebendig",
      accountBalance: "Kontostand",
      email: "E-Mail",
      phoneNumber: "Telefonnummer",
    },
    isOpen: "true",
    isEnabled: "true",
    filters: {
      firstName: { value: "Hans", add: false, remove: false },
      email: { value: "test@example.com", add: false, remove: false },
    },
    spatial: [],
    code: { age: "567" },
    integerKeys: ["accountBalance"],
    booleanProperty: ["alive"],
    titleForFilter: { firstName: "Vorname", email: "E-Mail" },
    start: {},
    end: {},
    temporal: {},
  },
};

export const HeaderOnlyDisabledAndClosed = {
  render: Header,
  args: {
    fields: {},
    isOpen: false,
    isEnabled: false,
    filters: {},
    spatial: [],
    code: { age: "567" },
    integerKeys: ["accountBalance"],
    booleanProperty: ["alive"],
    titleForFilter: {},
    start: {},
    end: {},
    temporal: {},
  },
};

export const HeaderOnlyClosed = {
  render: Header,
  args: {
    fields: {},
    isOpen: false,
    isEnabled: "true",
    filters: {},
    spatial: [],
    code: { age: "567" },
    integerKeys: ["accountBalance"],
    booleanProperty: ["alive"],
    titleForFilter: {},
    start: {},
    end: {},
    temporal: {},
  },
};
