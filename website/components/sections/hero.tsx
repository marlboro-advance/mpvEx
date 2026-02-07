/**
 * @file hero.tsx
 * @description Hero section component displaying the main value proposition.
 * Features grid background, spotlight effect, and subtle animations.
 * @module components/sections/hero
 */

"use client";

import { motion, type Variants } from "framer-motion";
import { Play } from "lucide-react";
import { Button } from "@/components/ui/button";
import { siteConfig } from "@/lib/site";
import { formatCompactNumber } from "@/lib/utils";

export function HeroSection({
  version,
  downloadUrl,
  stars,
  contributors,
}: {
  version?: string;
  downloadUrl?: string;
  stars?: number;
  contributors?: number;
}) {
  const containerVariants: Variants = {
    hidden: { opacity: 0 },
    show: {
      opacity: 1,
      transition: {
        staggerChildren: 0.15,
        delayChildren: 0.3,
      },
    },
  };

  const itemVariants: Variants = {
    hidden: { opacity: 0, y: 30, scale: 0.95 },
    show: {
      opacity: 1,
      y: 0,
      scale: 1,
      transition: {
        duration: 0.8,
        ease: [0.16, 1, 0.3, 1],
      },
    },
  };

  return (
    <section className="relative flex flex-col items-center pt-40 md:pt-48 pb-20 overflow-hidden bg-background">
      {/* Background Effects */}
      <div className="absolute inset-0 z-0">
        <div className="absolute inset-0 bg-grid-black/[0.05] dark:bg-grid-white/[0.05] bg-size-[40px_40px]" />

        {/* Intensified Spotlights */}
        <div className="absolute top-0 left-1/2 -translate-x-1/2 w-[1000px] h-[700px] opacity-50 blur-[120px] rounded-full bg-primary/30 pointer-events-none" />
        <div className="absolute top-1/4 left-1/3 w-[600px] h-[600px] opacity-30 blur-[100px] rounded-full bg-secondary/20 pointer-events-none" />

        {/* Subtle radial fade for depth */}
        <div className="absolute inset-0 bg-background/20 dark:bg-background/10" />
      </div>

      <motion.div
        variants={containerVariants}
        initial="hidden"
        animate="show"
        className="relative z-10 max-w-5xl mx-auto text-center px-4 sm:px-6 lg:px-8"
      >
        <motion.div
          variants={itemVariants}
          className="mb-8 flex justify-center"
        >
          <div className="relative group cursor-pointer">
            <div className="absolute -inset-1 bg-linear-to-r from-primary to-secondary rounded-2xl blur opacity-25 group-hover:opacity-60 transition duration-1000 group-hover:duration-200" />
            <div className="relative px-6 py-2 bg-background border border-border/50 rounded-full flex items-center space-x-2">
              <span className="flex h-2 w-2 relative">
                <span className="animate-ping absolute inline-flex h-full w-full rounded-full bg-primary opacity-75" />
                <span className="relative inline-flex rounded-full h-2 w-2 bg-primary" />
              </span>
              <span className="text-sm font-medium text-muted-foreground">
                Latest Version {version || siteConfig.version}
              </span>
            </div>
          </div>
        </motion.div>

        <motion.h1
          variants={itemVariants}
          className="text-5xl sm:text-7xl lg:text-8xl font-bold tracking-tight mb-6"
        >
          <span className="bg-clip-text text-transparent bg-linear-to-b from-foreground to-foreground/60">
            mpvExtended
          </span>
        </motion.h1>

        <motion.p
          variants={itemVariants}
          className="text-xl sm:text-2xl text-muted-foreground mb-10 max-w-2xl mx-auto leading-relaxed"
        >
          An advanced video player for Android built on libmpv. Experience
          Material3 design, background playback, and picture-in-picture.
        </motion.p>

        <motion.div
          variants={itemVariants}
          className="flex flex-col sm:flex-row gap-4 justify-center items-center mb-20"
        >
          <Button
            size="lg"
            className="h-12 px-8 text-base bg-primary hover:bg-primary/90 text-primary-foreground rounded-full transition-all duration-300 shadow-[0_0_20px_-5px_rgba(124,58,237,0.5)] hover:shadow-[0_0_30px_-5px_rgba(124,58,237,0.6)] cursor-pointer"
            onClick={() =>
              window.open(
                downloadUrl || siteConfig.links.latestRelease,
                "_blank",
              )
            }
          >
            <Play className="w-4 h-4 mr-2 fill-current" />
            Download Now
          </Button>
          <Button
            size="lg"
            variant="outline"
            className="h-12 px-8 text-base border-foreground/10 bg-foreground/5 hover:bg-foreground/10 text-foreground backdrop-blur-sm rounded-full transition-all duration-300"
            onClick={() => window.open(siteConfig.links.github, "_blank")}
          >
            View on GitHub
          </Button>
        </motion.div>

        {/* Stats Grid */}
        <motion.div
          variants={itemVariants}
          className="flex flex-wrap justify-center gap-8 md:gap-12 py-8 border-t border-foreground/5 max-w-4xl mx-auto"
        >
          <div className="flex flex-col items-center min-w-[100px]">
            <span className="text-3xl font-bold text-foreground mb-1">
              Apache 2.0
            </span>
            <span className="text-sm text-muted-foreground">License</span>
          </div>
          <div className="flex flex-col items-center min-w-[100px]">
            <span className="text-3xl font-bold text-foreground mb-1">
              Libmpv
            </span>
            <span className="text-sm text-muted-foreground">Powered</span>
          </div>
          <div className="flex flex-col items-center min-w-[100px]">
            <span className="text-3xl font-bold text-foreground mb-1">M3</span>
            <span className="text-sm text-muted-foreground">
              Material Design
            </span>
          </div>
          <div className="flex flex-col items-center min-w-[100px]">
            <span className="text-3xl font-bold text-foreground mb-1">
              {stars ? formatCompactNumber(stars) : "100+"}
            </span>
            <span className="text-sm text-muted-foreground">GitHub Stars</span>
          </div>
          <div className="flex flex-col items-center min-w-[100px]">
            <span className="text-3xl font-bold text-foreground mb-1">
              {contributors ? formatCompactNumber(contributors) : "10+"}
            </span>
            <span className="text-sm text-muted-foreground">Contributors</span>
          </div>
        </motion.div>
      </motion.div>
    </section>
  );
}
