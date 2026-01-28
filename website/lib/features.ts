import { ImageIcon, Layers, Lock, Play, Radio, Settings, Smartphone, Zap } from "lucide-react";

export interface Feature {
  id: string;
  title: string;
  description: string;
  icon: typeof Play;
  benefits: string[];
}

export const features: Feature[] = [
  {
    id: "material-design",
    title: "Material Design",
    description: "Modern, expressive UI that follows the latest Android design guidelines.",
    icon: Play,
    benefits: ["Intuitive interface", "Consistent experience", "Modern aesthetic"],
  },
  {
    id: "advanced-config",
    title: "Advanced Config",
    description: "Full control over mpv configuration and scripting capabilities.",
    icon: Settings,
    benefits: ["Full customization", "Advanced controls", "Script support"],
  },
  {
    id: "picture-in-picture",
    title: "Picture-in-Picture",
    description: "Watch your videos while multitasking with seamless PiP support.",
    icon: ImageIcon,
    benefits: ["Multitasking", "Seamless PiP", "Background playback"],
  },
  {
    id: "background-playback",
    title: "Background Playback",
    description: "Listen to your media in the background with audio-only mode.",
    icon: Zap,
    benefits: ["Audio mode", "Battery efficient", "Always accessible"],
  },
  {
    id: "network-streaming",
    title: "Network Streaming",
    description: "Stream videos directly from the network with high-quality rendering.",
    icon: Radio,
    benefits: ["Stream anywhere", "High quality", "Reliable connection"],
  },
  {
    id: "subtitle-support",
    title: "Subtitle Support",
    description: "Extensive subtitle support for external and embedded subtitles.",
    icon: Layers,
    benefits: ["Multiple formats", "Easy selection", "Customizable styling"],
  },
  {
    id: "file-management",
    title: "File Management",
    description: "Built-in media picker with file and folder view modes.",
    icon: Smartphone,
    benefits: ["Easy browsing", "Quick access", "Organized library"],
  },
  {
    id: "ad-free",
    title: "Ad-Free",
    description: "Completely free and open source without any ads or excessive permissions.",
    icon: Lock,
    benefits: ["No ads", "Privacy focused", "Open source"],
  },
];
