export interface FilterValue {
  value: string;
  add: boolean;
  remove: boolean;
}

export type Filters = Record<string, FilterValue>;

export interface ChangedValueEntry {
  filterKey: string;
  value: string;
}

export type ChangedValue = Record<string, ChangedValueEntry>;

/** Maps a field key to its enum of allowed values (from the collection's JSON schema). */
export type Code = Record<string, string[]>;
