/**
 * @file data.ts
 * @description Static data definitions for the application, including features, download options, and statistics.
 * Used to populate UI components with content.
 * @module lib/data
 */

export const features = [
  {
    id: 1,
    title: "Material3 Design",
    description:
      "Modern, expressive UI following latest Android design guidelines.",
    icon: "palette",
  },
  {
    id: 2,
    title: "Advanced Configuration",
    description:
      "Full control over mpv configuration and scripting capabilities.",
    icon: "sliders",
  },
  {
    id: 3,
    title: "Picture-in-Picture",
    description: "Watch videos while multitasking with seamless PiP support.",
    icon: "frame",
  },
  {
    id: 4,
    title: "Background Playback",
    description:
      "Listen to audio content while using other apps with audio-only mode.",
    icon: "volume-2",
  },
  {
    id: 5,
    title: "File Management",
    description:
      "Media picker with tree and folder view modes for easy browsing.",
    icon: "folder",
  },
  {
    id: 6,
    title: "Subtitle Support",
    description:
      "External subtitle support with multiple format compatibility.",
    icon: "captions",
  },
  {
    id: 7,
    title: "Network Streaming",
    description: "Stream from SMB/FTP/WebDAV with high-quality rendering.",
    icon: "wifi",
  },
  {
    id: 8,
    title: "Custom Playlists",
    description: "Create and manage custom playlist collections effortlessly.",
    icon: "list",
  },
  {
    id: 9,
    title: "Zoom Gestures",
    description: "Intuitive zoom and pan controls with gesture support.",
    icon: "maximize-2",
  },
  {
    id: 10,
    title: "External Audio",
    description:
      "Support for external audio tracks and audio stream selection.",
    icon: "headphones",
  },
  {
    id: 11,
    title: "Search Functionality",
    description: "Quick search to find your media files and content.",
    icon: "search",
  },
  {
    id: 12,
    title: "Free & Open Source",
    description:
      "Completely free, open source, with zero ads or excessive permissions.",
    icon: "shield-check",
  },
];

//Site Config Imports

import { siteConfig } from "@/lib/site";

export const downloadOptions = [
  {
    id: 1,
    title: "Stable Release",
    description: "Download the latest stable version directly from GitHub.",
    link: siteConfig.links.latestRelease,
    icon: "github",
    cta: "Download APK",
  },
  {
    id: 2,
    title: "Preview Builds",
    description: "Test the latest features and improvements in development.",
    link: siteConfig.links.releases,
    icon: "zap",
    cta: "View Pre-releases",
  },
  {
    id: 3,
    title: "IzzyOnAndroid",
    description:
      "Install and update automatically on the IzzyOnAndroid client.",
    link: siteConfig.links.izzyOnAndroid,
    icon: "download",
    cta: "View Repository",
  },
];

export const stats = [
  {
    label: "Stars",
    value: "21.7K",
    icon: "⭐",
  },
  {
    label: "Downloads",
    value: "5.1K",
    icon: "📥",
  },
  {
    label: "Contributors",
    value: "25+",
    icon: "👥",
  },
  {
    label: "Open Source",
    value: "Apache 2.0",
    icon: "📄",
  },
];
