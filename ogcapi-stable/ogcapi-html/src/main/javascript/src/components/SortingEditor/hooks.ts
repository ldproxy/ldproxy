import { useEffect, useState } from "react";

export interface ApiError {
  status: number;
  message: string;
}

export interface UseApiInfoResult<T> {
  obj: T | null;
  isLoaded: boolean;
  error: ApiError | unknown;
}

export const useApiInfo = <T = unknown>(url: string | URL): UseApiInfoResult<T> => {
  // TODO: custom hook that encapsulates fetch logic from FetchingPropertiesEnum + FetchingSpatialTemporal
  // return the plain json object, extraction of fields/codes/bounds etc. goes to util.js

  const [obj, setObj] = useState<T | null>(null);
  const [isLoaded, setIsLoaded] = useState(false);
  const [error, setError] = useState<ApiError | unknown>(null);

  useEffect(() => {
    fetch(url)
      .then((response) => {
        if (!response.ok) {
          setError({ status: response.status, message: response.statusText });
          return null;
        }
        return response.json();
      })
      .then((data) => {
        if (data) {
          setObj(data);
          setIsLoaded(true);
        }
      })
      .catch((errors) => setError(errors));
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, []);

  return { obj, isLoaded, error };
};

export const useDebounce = <T>(value: T, delay: number): T => {
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
    [value, delay], // Only re-call effect if value or delay changes
  );
  return debouncedValue;
};
