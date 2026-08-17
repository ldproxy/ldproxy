// Ambient module declarations for packages with no shipped types and no @types package
// available. Kept in a separate file from global.d.ts: co-locating a `declare module "..."`
// ambient override with a `declare global { ... }` augmentation in the same file makes
// TypeScript's module resolution ignore the override entirely and fall through to the real
// (untyped) source file instead - verified in isolation, not documented behavior.

interface JQuerySelection {
  tooltip(): JQuerySelection;
}

interface JQueryStatic {
  (selector: string): JQuerySelection;
  (callback: () => void): void;
}

declare module "jquery" {
  const $: JQueryStatic;
  export = $;
}

declare module "bootstrap";
