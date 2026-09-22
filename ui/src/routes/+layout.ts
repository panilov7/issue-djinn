// SPA-only app: no server-side rendering, no static prerender.
// Quinoa serves the static shell and Quarkus the /api + /mcp backends.
export const ssr = false;
export const prerender = false;
