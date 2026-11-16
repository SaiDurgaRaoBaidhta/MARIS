import "@testing-library/jest-dom/vitest";
import { cleanup, configure } from "@testing-library/react";
import { afterEach, vi } from "vitest";

afterEach(() => { cleanup(); vi.restoreAllMocks(); });

// CI runners are slower than a laptop: give findBy*/waitFor time for the scripted middleware to answer
configure({ asyncUtilTimeout: 5000 });
// jsdom has no scrolling; opening a page scrolls to the top
window.scrollTo = () => {};
