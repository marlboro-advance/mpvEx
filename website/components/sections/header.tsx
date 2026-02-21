/**
 * @file header.tsx
 * @description Application Header/Navbar component with responsive design.
 * Features scroll-aware styling, navigation links, and theme toggle.
 * @module components/sections/header
 */

"use client";

import { useMotionValueEvent, useScroll } from "framer-motion";
import { Github, Menu, Star, X } from "lucide-react";
import Image from "next/image";
import Link from "next/link";
import { useState } from "react";
import { ThemeToggle } from "@/components/theme-toggle";
import { Button } from "@/components/ui/button";
import { siteConfig } from "@/lib/site";

export function Header({ downloadUrl }: { downloadUrl?: string }) {
  const [isScrolled, setIsScrolled] = useState(false);
  const [isOpen, setIsOpen] = useState(false);
  const { scrollY } = useScroll();

  useMotionValueEvent(scrollY, "change", (latest) => {
    setIsScrolled(latest > 50);
  });

  return (
    <header className="fixed top-4 left-4 right-4 z-50 md:top-6 md:left-6 md:right-6">
      <nav
        className={`mx-auto px-6 py-4 rounded-2xl flex items-center justify-between transition-all duration-500 ${
          isScrolled
            ? "bg-background/80 backdrop-blur-2xl border border-foreground/10 shadow-2xl"
            : "bg-background/40 backdrop-blur-xl border border-foreground/5 shadow-lg"
        }`}
      >
        {/* Logo */}
        {/** biome-ignore lint/a11y/useSemanticElements: interactive div used for complex logo layout */}
        <div
          className="flex items-center gap-3 cursor-pointer group"
          onClick={() => window.scrollTo({ top: 0, behavior: "smooth" })}
          role="button"
          tabIndex={0}
          onKeyDown={(e) => {
            if (e.key === "Enter" || e.key === " ") {
              window.scrollTo({ top: 0, behavior: "smooth" });
            }
          }}
        >
          <div className="relative w-10 h-10 flex items-center justify-center shrink-0 bg-foreground/5 dark:bg-white/5 rounded-xl overflow-hidden border border-border/50 shadow-sm transition-transform duration-300 group-hover:scale-110">
            <Image
              src="/icon.png"
              alt="mpvEx Logo"
              width={40}
              height={40}
              priority
              className="object-cover"
            />
          </div>
          <span className="text-foreground font-bold text-lg tracking-tight group-hover:text-primary transition-colors">
            mpvEx
          </span>
        </div>

        {/* Desktop Navigation - Absolute Center */}
        <div className="hidden lg:flex items-center gap-2 absolute left-1/2 transform -translate-x-1/2">
          {[
            { name: "Features", href: "#features" },
            { name: "Screenshots", href: "#screenshots" },
            { name: "Contributors", href: "#contributors" },
          ].map((item) => (
            <Link
              key={item.name}
              href={item.href}
              className="text-foreground/80 hover:text-primary transition-all text-sm px-4 py-2 hover:bg-foreground/5 rounded-full relative overflow-hidden group font-bold"
              onClick={(e) => {
                e.preventDefault();
                document
                  .querySelector(item.href)
                  ?.scrollIntoView({ behavior: "smooth" });
              }}
            >
              <span className="relative z-10">{item.name}</span>
              <div className="absolute inset-0 bg-primary/10 translate-y-full group-hover:translate-y-0 transition-transform duration-300" />
            </Link>
          ))}
        </div>

        {/* Right Actions */}
        <div className="hidden md:flex items-center gap-3">
          {/* Star on GitHub */}
          <button
            onClick={() => window.open(siteConfig.links.github, "_blank")}
            className="flex items-center gap-2 px-4 py-2 rounded-xl bg-foreground/5 hover:bg-foreground/10 transition-all text-sm text-foreground/80 hover:text-foreground border border-transparent hover:border-foreground/10"
          >
            <Star className="w-4 h-4 fill-primary/20 text-primary" />
            <span className="hidden sm:inline">Star</span>
          </button>

          {/* Theme Toggle */}
          <ThemeToggle />

          {/* GitHub Link */}
          <a
            href={siteConfig.links.github}
            target="_blank"
            rel="noopener noreferrer"
            className="p-2 rounded-xl bg-foreground/5 hover:bg-foreground/10 transition-all text-foreground/80 hover:text-foreground border border-transparent hover:border-foreground/10"
          >
            <Github className="w-5 h-5" />
          </a>

          {/* Download Button */}
          <Button
            className="bg-primary hover:bg-primary/90 text-primary-foreground rounded-xl transition-all duration-300 shadow-[0_0_20px_-5px_hsl(var(--primary)/0.5)] active:scale-95"
            onClick={() =>
              window.open(
                downloadUrl || siteConfig.links.latestRelease,
                "_blank",
              )
            }
          >
            Download
          </Button>
        </div>

        {/* Mobile Actions */}
        <div className="lg:hidden flex items-center gap-2">
          <div className="md:hidden p-1 flex justify-center">
            <ThemeToggle />
          </div>
          <button
            onClick={() => setIsOpen(!isOpen)}
            className="p-2 w-10 h-10 flex items-center justify-center bg-foreground/5 hover:bg-foreground/10 rounded-2xl transition-all active:scale-95 font-bold"
            aria-label="Toggle Menu"
          >
            {isOpen ? <X className="w-5 h-5" /> : <Menu className="w-5 h-5" />}
          </button>
        </div>
      </nav>

      {/* Mobile Menu */}
      {isOpen && (
        <div className="lg:hidden mt-4 bg-background/60 backdrop-blur-3xl border border-foreground/10 rounded-3xl py-6 px-6 relative z-50 shadow-2xl overflow-hidden">
          {/* Background Decorative Element */}
          <div className="absolute top-0 right-0 w-32 h-32 bg-primary/10 blur-3xl rounded-full -mr-16 -mt-16 pointer-events-none" />
          <div className="absolute bottom-0 left-0 w-32 h-32 bg-secondary/10 blur-3xl rounded-full -ml-16 -mb-16 pointer-events-none" />

          <div className="flex flex-col gap-2 relative z-10">
            {[
              { name: "Features", href: "#features" },
              { name: "Screenshots", href: "#screenshots" },
              { name: "Contributors", href: "#contributors" },
            ].map((item) => (
              <div key={item.name}>
                <Link
                  href={item.href}
                  className="flex items-center justify-between text-lg font-medium text-foreground/80 hover:text-primary transition-colors py-3 px-4 rounded-2xl hover:bg-foreground/5 group"
                  onClick={(e) => {
                    e.preventDefault();
                    setIsOpen(false);
                    document
                      .querySelector(item.href)
                      ?.scrollIntoView({ behavior: "smooth" });
                  }}
                >
                  {item.name}
                  <div className="w-2 h-2 rounded-full bg-primary/20 group-hover:bg-primary transition-colors" />
                </Link>
              </div>
            ))}

            <div className="h-px bg-foreground/10 my-2 md:hidden" />

            <div className="flex items-center gap-3 pt-2 md:hidden">
              <Button
                className="flex-2 h-14 bg-primary hover:bg-primary/90 text-primary-foreground rounded-2xl shadow-lg active:scale-95 transition-all"
                onClick={() => {
                  window.open(
                    downloadUrl || siteConfig.links.latestRelease,
                    "_blank",
                  );
                  setIsOpen(false);
                }}
              >
                Download Latest
              </Button>
            </div>

            <div className="flex justify-center gap-6 mt-4 opacity-60 md:hidden">
              <a
                href={siteConfig.links.github}
                target="_blank"
                rel="noreferrer"
              >
                <Github className="w-5 h-5 text-foreground" />
              </a>
              <a
                href={siteConfig.links.github}
                target="_blank"
                rel="noreferrer"
                className="flex items-center gap-1 text-xs font-medium"
              >
                <Star className="w-4 h-4" />
                Star on GitHub
              </a>
            </div>
          </div>
        </div>
      )}
    </header>
  );
}
