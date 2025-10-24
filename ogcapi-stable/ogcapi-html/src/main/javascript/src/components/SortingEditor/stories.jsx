import React from "react";
import { storiesOf } from "@storybook/react";
import Editor from "./Editor";
import EditorHeader from "./Editor/Header";

const Template = (args) => {
  const deleteFilters = (field) => () => {
    // eslint-disable-next-line no-undef
    setFilters((current) => {
      const copy = { ...current };
      delete copy[field];
      return copy;
    });
  };
  return (
    <>
      <EditorHeader {...args} />
      <Editor {...args} deleteFilters={deleteFilters} />
    </>
  );
};

const Header = (args) => {
  return (
    <>
      <EditorHeader {...args} />
    </>
  );
};

storiesOf("@ogcapi/html/FilterEditor/Plain", module).add("Default", () => (
  <Template
    fields={{
      firstName: "Vorname",
      lastName: "Nachname",
      age: "Alter",
      alive: "Lebendig",
      accountBalance: "Kontostand",
    }}
    isOpen="true"
    isEnabled="true"
    filters={{}}
    spatial={[
      [5.719412969894958, 50.31135979170666],
      [9.46927842749998, 53.15055217399161],
    ]}
    code={{ age: "567" }}
    integerKeys={["accountBalance"]}
    booleanProperty={["alive"]}
    titleForFilter={{
      firstName: "Vorname",
      lastName: "Nachname",
      age: "Alter",
      alive: "Lebendig",
      accountBalance: "Kontostand",
    }}
    start={1666375200000}
    end={1666375200000}
    temporal={{
      start: 1666375200000,
      end: 1666375200000,
    }}
  />
));

storiesOf("@ogcapi/html/FilterEditor/One Filter", module).add("With Custom Fields", () => (
  <Template
    fields={{
      firstName: "Vorname",
      lastName: "Nachname",
      age: "Alter",
      alive: "Lebendig",
      accountBalance: "Kontostand",
    }}
    isOpen="true"
    isEnabled="true"
    filters={{ firstName: { value: "Hans", add: false, remove: false } }}
    spatial={[]}
    code={{ age: "567" }}
    integerKeys={["accountBalance"]}
    booleanProperty={["alive"]}
    titleForFilter={{
      firstName: "Vorname",
      lastName: "Nachname",
      age: "Alter",
      alive: "Lebendig",
      accountBalance: "Kontostand",
    }}
    start={{}}
    end={{}}
    temporal={{}}
  />
));
storiesOf("@ogcapi/html/FilterEditor/All Fields in Filters", module).add(
  "With Custom Fields",
  () => (
    <Template
      fields={{
        firstName: "Vorname",
        lastName: "Nachname",
        age: "Alter",
        alive: "Lebendig",
        accountBalance: "Kontostand",
      }}
      isOpen="true"
      isEnabled="true"
      filters={{
        firstName: { value: "Hans", add: false, remove: false },
        accountBalance: { value: 1000, add: false, remove: false },
        lastName: { value: "Zahnen", add: false, remove: false },
        age: { value: 27, add: false, remove: false },
        alive: { value: true, add: false, remove: false },
      }}
      changedValue={{ firstName: { filterKey: "firstName", value: "Han" } }}
      spatial={[]}
      code={{ age: "567" }}
      integerKeys={["accountBalance"]}
      booleanProperty={["alive"]}
      titleForFilter={{
        firstName: "Vorname",
        lastName: "Nachname",
        age: "Alter",
        alive: "Lebendig",
        accountBalance: "Kontostand",
      }}
      start={{}}
      end={{}}
      temporal={{}}
    />
  )
);
storiesOf("@ogcapi/html/FilterEditor/Only Bbox", module).add("With Custom Fields", () => (
  <Template
    fields={{}}
    isOpen="true"
    isEnabled="true"
    filters={{}}
    spatial={[
      [5.719412969894958, 50.31135979170666],
      [9.46927842749998, 53.15055217399161],
    ]}
    code={{ age: "567" }}
    integerKeys={["accountBalance"]}
    booleanProperty={["alive"]}
    titleForFilter={{
      firstName: "Vorname",
      lastName: "Nachname",
      age: "Alter",
      alive: "Lebendig",
      accountBalance: "Kontostand",
    }}
    start={{}}
    end={{}}
    temporal={{}}
  />
));
storiesOf("@ogcapi/html/FilterEditor/Only Temporal", module).add("With Custom Fields", () => (
  <Template
    fields={{}}
    isOpen="true"
    isEnabled="true"
    filters={{}}
    spatial={[]}
    code={{ age: "567" }}
    integerKeys={["accountBalance"]}
    booleanProperty={["alive"]}
    titleForFilter={{}}
    start={1666375200000}
    end={1666375200000}
    temporal={{ start: 1666375200000, end: 1666375200000 }}
  />
));

// Aber hier nur Header:

storiesOf("@ogcapi/html/Only Header/Without Filters", module).add("Header Only", () => (
  <Header
    fields={{}}
    isOpen="true"
    isEnabled="true"
    filters={{}}
    spatial={[]}
    code={{ age: "567" }}
    integerKeys={["accountBalance"]}
    booleanProperty={["alive"]}
    titleForFilter={{}}
    start={1666375200000}
    end={1666375200000}
    temporal={{
      start: 1666375200000,
    }}
  />
));

storiesOf("@ogcapi/html/Only Header/With one Filter", module).add("With Custom Fields", () => (
  <Header
    fields={{
      firstName: "Vorname",
    }}
    isOpen="true"
    isEnabled="true"
    filters={{
      firstName: { value: "Hans", add: false, remove: false },
    }}
    spatial={[]}
    code={{ age: "567" }}
    integerKeys={["accountBalance"]}
    booleanProperty={["alive"]}
    titleForFilter={{
      firstName: "Vorname",
    }}
    start={1666375200000}
    end={1666375200000}
    temporal={{}}
  />
));
storiesOf("@ogcapi/html/Only Header/With two Filters", module).add("With Custom Fields", () => (
  <Header
    fields={{
      firstName: "Vorname",
      lastName: "Nachname",
      age: "Alter",
      alive: "Lebendig",
      accountBalance: "Kontostand",
      email: "E-Mail",
      phoneNumber: "Telefonnummer",
    }}
    isOpen="true"
    isEnabled="true"
    filters={{
      firstName: { value: "Hans", add: false, remove: false },
      email: { value: "test@example.com", add: false, remove: false },
    }}
    spatial={[]}
    code={{ age: "567" }}
    integerKeys={["accountBalance"]}
    booleanProperty={["alive"]}
    titleForFilter={{
      firstName: "Vorname",
      email: "E-Mail",
    }}
    start={{}}
    end={{}}
    temporal={{}}
  />
));
storiesOf("@ogcapi/html/Only Header/isEnabled=false + isOpen=false", module).add(
  "With Custom Fields",
  () => (
    <Header
      fields={{}}
      isOpen={false}
      isEnabled={false}
      filters={{}}
      spatial={[]}
      code={{ age: "567" }}
      integerKeys={["accountBalance"]}
      booleanProperty={["alive"]}
      titleForFilter={{}}
      start={{}}
      end={{}}
      temporal={{}}
    />
  )
);
storiesOf("@ogcapi/html/Only Header/isOpen=false", module).add("With Custom Fields", () => (
  <Header
    fields={{}}
    isOpen={false}
    isEnabled="true"
    filters={{}}
    spatial={[]}
    code={{ age: "567" }}
    integerKeys={["accountBalance"]}
    booleanProperty={["alive"]}
    titleForFilter={{}}
    start={{}}
    end={{}}
    temporal={{}}
  />
));
