import { useEffect, useState } from "react";

export const useApiInfo = (url) => {
  // TODO: custom hook that encapsulates fetch logic from FetchingPropertiesEnum + FetchingSpatialTemporal
  // return the plain json object, extraction of fields/codes/bounds etc. goes to util.js

  const [obj, setObj] = useState(null);
  const [isLoaded, setIsLoaded] = useState(false);
  const [error, setError] = useState(null);

  useEffect(() => {
    fetch(url)
      .then((response) => response.json())
      .then((data) => {
        setObj(data);
        setIsLoaded(true);
      })
      .catch((errors) => setError(errors));
  }, []);

  return { obj, isLoaded, error };
};

export const useDebounce = (value, delay) => {
  // State and setters for debounced value
  const [debouncedValue, setDebouncedValue] = useState(value);
  useEffect(
    () => {
      // Update debounced value after delay
      const handler = setTimeout(() => {
        setDebouncedValue(value);
      }, delay);
      // Cancel the timeout if value changes (also on delay change or unmount)
      // This is how we prevent debounced value from updating if value is changed ...
      // .. within the delay period. Timeout gets cleared and restarted.
      return () => {
        clearTimeout(handler);
      };
    },
    [value, delay] // Only re-call effect if value or delay changes
  );
  return debouncedValue;
};
