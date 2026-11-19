import qs from "qs";

// In the value of a query parameter, an unencoded comma separates the items of a list and an
// encoded comma (%2C) is a comma within an item. qs decodes both to a comma and encodes every
// comma, so a query that is parsed and written again would turn the separators into commas within
// one item. The query is therefore parsed with U+001F (%1F) for an encoded comma, and an
// unencoded comma is written for a comma and an encoded comma for U+001F, as in the server.

// with a decoder, the typings of qs no longer know that the values are strings
export const parseQuery = (search: string): qs.ParsedQs =>
  qs.parse(search, {
    ignoreQueryPrefix: true,
    decoder: (str, defaultDecoder, charset, type) =>
      defaultDecoder(type === "value" ? str.replace(/%2C/gi, "%1F") : str, defaultDecoder, charset),
  }) as qs.ParsedQs;

export const stringifyQuery = (query: qs.ParsedQs): string =>
  qs.stringify(query, {
    addQueryPrefix: true,
    encoder: (str, defaultEncoder, charset, type) => {
      const encoded = defaultEncoder(str, defaultEncoder, charset);
      return type === "value" ? encoded.replace(/%2C/g, ",").replace(/%1F/g, "%2C") : encoded;
    },
  });
