export const getBaseUrl = () => {
  let baseUrl = new URL(window.location.href);
  if (process.env.NODE_ENV !== "production") {
    baseUrl = new URL(
      "https://demo.ldproxy.net/cshapes/collections/boundary/items?limit=10&offset=10"
    );
  }
  return baseUrl;
};

export const extractFields = (obj) => {
  const fields = {};
  const code = {};
  const integerKeys = [];
  const booleanProperty = [];
  if (obj && obj.properties) {
    // eslint-disable-next-line
    for (const key in obj.properties) {
      if (
        obj.properties[key]["x-ogc-role"] &&
        obj.properties[key]["x-ogc-role"].startsWith("primary-")
      ) {
        // eslint-disable-next-line no-continue
        continue;
      }
      if (obj.properties[key].title) {
        fields[key] = obj.properties[key].title;
      }
      if (obj.properties[key].enum) {
        code[key] = obj.properties[key].enum;
      }
      if (obj.properties[key].type === "integer") {
        integerKeys.push(key);
      }
      if (obj.properties[key].type === "boolean") {
        booleanProperty.push(key);
      }
    }
  }

  return { fields, code, integerKeys, booleanProperty };
};
