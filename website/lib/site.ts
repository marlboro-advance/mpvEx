/**
 * @file site.ts
 * @description Central configuration file for the website's metadata, links, and constant values.
 * @module lib/site
 */

/**
 * Global site configuration object.
 * Contains metadata, external links, and author information used throughout the application.
 */
export const siteConfig = {
  name: "mpvExtended",
  version: "v1.2.7",
  description:
    "Advanced mpv-based video player for Android with powerful features, seamless playback, and open-source freedom.",
  url: "https://mpvex.vercel.app",
  ogImage: "https://mpvex.vercel.app/og.jpg",
  icons: {
    icon: "/icon.svg",
    apple: "/apple-icon.png",
  },
  links: {
    github: "https://github.com/marlboro-advance/mpvEx",
    releases: "https://github.com/marlboro-advance/mpvEx/releases",
    latestRelease: "https://github.com/marlboro-advance/mpvEx/releases/latest",
    izzyOnAndroid: "https://apt.izzysoft.de/packages/app.marlboroadvance.mpvex",
    contributors: "https://github.com/marlboro-advance/mpvEx/graphs/contributors",
  },
  author: {
    name: "marlboro-advance",
    url: "https://github.com/marlboro-advance",
  },
} as const;

export type SiteConfig = typeof siteConfig;
