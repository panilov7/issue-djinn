import js from '@eslint/js';
import svelte from 'eslint-plugin-svelte';
import globals from 'globals';
import tseslint from 'typescript-eslint';

export default tseslint.config(
	{
		ignores: ['.svelte-kit/', 'build/', 'node_modules/']
	},
	js.configs.recommended,
	...tseslint.configs.recommended,
	...svelte.configs['flat/recommended'],
	{
		files: ['**/*.svelte'],
		languageOptions: {
			parserOptions: {
				parser: tseslint.parser
			}
		}
	},
	{
		// `.svelte.ts` / `.svelte.js` are rune-bearing modules; route them through
		// the Svelte processor so the inner script is parsed as TypeScript.
		files: ['**/*.svelte.ts', '**/*.svelte.js'],
		processor: 'svelte/svelte',
		languageOptions: {
			parserOptions: {
				parser: tseslint.parser
			}
		}
	},
	{
		languageOptions: {
			globals: {
				...globals.browser,
				...globals.node
			}
		},
		rules: {
			// Maintainability baseline: explicit `any` is banned.
			'@typescript-eslint/no-explicit-any': 'error',
			// This is a static SPA and the shadcn `Button` primitive supports `href`
			// links (used for the header nav). Plain hrefs are correct here.
			'svelte/no-navigation-without-resolve': 'off',
			// `no-useless-escape` misfires on the bracket character class in
			// `excerpt.ts` — the escaped `\[`, `\(`, `\)` there ARE required for
			// the regex to parse correctly.
			'no-useless-escape': 'off'
		}
	}
);
