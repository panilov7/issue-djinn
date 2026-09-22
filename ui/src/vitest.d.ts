// Ambient types for the test suite.
// `globals: true` is enabled in vite.config.ts, so vitest injects
// describe/it/expect/beforeEach/afterEach at runtime; these references make
// them type-safe and pull in the @testing-library/jest-dom matchers
// (`toBeInTheDocument`, ...).

/// <reference types="vitest/globals" />
/// <reference types="@testing-library/jest-dom/vitest" />
