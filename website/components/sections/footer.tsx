"use client";

import { Github, Heart } from "lucide-react";

export function Footer() {
  const currentYear = new Date().getFullYear();

  return (
    <footer className="bg-background border-t border-foreground/5 py-16 px-4 sm:px-6 lg:px-8 relative overflow-hidden">
      <div className="absolute inset-0 bg-grid-black/[0.01] dark:bg-grid-white/[0.01] bg-size-[32px_32px] pointer-events-none" />
      <div className="max-w-7xl mx-auto relative z-10">
        {/* Acknowledgments Section */}
        <div className="text-center mb-12">
          <h3 className="text-lg font-semibold text-foreground mb-4">
            Acknowledgments
          </h3>
          <div className="flex flex-wrap items-center justify-center gap-6">
            <a
              href="https://github.com/mpv-android/mpv-android"
              target="_blank"
              rel="noopener noreferrer"
              className="text-sm text-muted-foreground hover:text-primary transition-colors"
            >
              mpv-android
            </a>
            <span className="text-muted-foreground/30">•</span>
            <a
              href="https://github.com/abdallahmehiz/mpvKt"
              target="_blank"
              rel="noopener noreferrer"
              className="text-sm text-muted-foreground hover:text-primary transition-colors"
            >
              mpvKt
            </a>
            <span className="text-muted-foreground/30">•</span>
            <a
              href="https://github.com/anilbeesetti/nextplayer"
              target="_blank"
              rel="noopener noreferrer"
              className="text-sm text-muted-foreground hover:text-primary transition-colors"
            >
              Next player
            </a>
            <span className="text-muted-foreground/30">•</span>
            <a
              href="https://github.com/FoedusProgramme/Gramophone"
              target="_blank"
              rel="noopener noreferrer"
              className="text-sm text-muted-foreground hover:text-primary transition-colors"
            >
              Gramophone
            </a>
          </div>
        </div>

        {/* Divider */}
        <div className="border-t border-border/20 my-8" />

        {/* Bottom Section */}
        <div className="flex flex-col sm:flex-row items-center justify-center gap-6 text-center sm:text-left">
          <p className="text-sm text-muted-foreground">
            Made with
            <Heart
              className="inline w-4 h-4 text-red-500 mx-1"
              fill="currentColor"
            />
            by{" "}
            <a
              href="https://riteshdpandit.vercel.app/"
              target="_blank"
              rel="noopener noreferrer"
              className="hover:text-primary transition-colors font-medium decoration-primary/30 underline-offset-4 hover:underline"
            >
              Ritesh Pandit
            </a>
          </p>
          <span className="hidden sm:inline text-muted-foreground/30">•</span>
          <a
            href="https://github.com/Riteshp2001"
            target="_blank"
            rel="noopener noreferrer"
            className="text-sm text-muted-foreground hover:text-primary transition-colors flex items-center gap-2 justify-center"
          >
            <Github className="w-4 h-4" />
            GitHub
          </a>
          <span className="text-muted-foreground/30">•</span>
          <p className="text-sm text-muted-foreground">
            {`© ${currentYear} mpvExtended Project. Apache 2.0 License`}
          </p>
        </div>
      </div>
    </footer>
  );
}
