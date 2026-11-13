import type { Config } from "tailwindcss";

/* Tailwind v4 reads this through `@config` in src/styles/index.css. Dark mode is the `dark:` variant defined there
 * (<html data-theme="dark">), as in the reference UI. */
export default {
  content: ["./index.html", "./src/**/*.{ts,tsx}"],
  theme: {
    extend: {
      fontFamily: {
        sans: ["Roboto", "Arial", "sans-serif"],
        mono: ['"JetBrains Mono"', "ui-monospace", "monospace"],
      },
      colors: {
        // BART palette
        navy: "#0b2545",
        bart: { DEFAULT: "#0099d8", 600: "#0080c0", 700: "#00679e" },
        sky: { DEFAULT: "#e6f5fc", 2: "#cdeaf7" },
        teal: { DEFAULT: "#00a9e0" },
        // dark theme surfaces
        "ink-dark": "#e6edf5",
        "panel-dark": "#0f1e2e",
        "line-dark": "#22364b",
        "chip-dark": "#12233a",
      },
    },
  },
} satisfies Config;
