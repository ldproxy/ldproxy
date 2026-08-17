/** @type {import('@storybook/react-vite').StorybookConfig} */
const config = {
  stories: ['../src/**/stories.@(js|jsx)'],
  framework: {
    name: '@storybook/react-vite',
    options: {},
  },
};

export default config;
