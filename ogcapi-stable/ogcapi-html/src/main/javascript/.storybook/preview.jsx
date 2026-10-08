// eslint-disable-next-line no-unused-vars -- React must be in scope for the classic JSX runtime
import React from 'react';

export const decorators = [
    (Story) => (
        <div
            style={{
                height: '65vh',
                width: '100%',
            }}>
            <Story />
        </div>
    ),
];

export const parameters = {
    actions: { argTypesRegex: '^on[A-Z].*' },
    controls: { expanded: true },
};
