import adapter from '@sveltejs/adapter-static';
import { vitePreprocess } from '@sveltejs/vite-plugin-svelte';

/** @type {import('@sveltejs/kit').Config} */
const config = {
	preprocess: vitePreprocess(),
	kit: {
		adapter: adapter({
			// Quinoa's SPA routing re-routes unknown paths to `index.html` — the
			// fallback file MUST be named `index.html` (the #18 research brief's
			// `200.html` convention does not work with Quinoa).
			fallback: 'index.html',
			strict: true
		}),
		alias: {
			$lib: './src/lib'
		},
		paths: {
			relative: false
		}
	}
};

export default config;
