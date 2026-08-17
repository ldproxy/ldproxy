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
