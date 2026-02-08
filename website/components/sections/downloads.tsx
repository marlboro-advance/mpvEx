"use client";

import { motion } from "framer-motion";
import { ArrowRight, FlaskConical, Github } from "lucide-react";
import Image from "next/image";
import { Button } from "@/components/ui/button";
import { downloadOptions } from "@/lib/data";

const iconMap: Record<string, React.ReactNode> = {
  github: <Github className="w-8 h-8 text-foreground" />,
  zap: <FlaskConical className="w-8 h-8 text-red-500" />,
  download: (
    <div className="relative w-32 h-12">
      <Image
        src="/izzy.svg"
        alt="Get it at IzzyOnDroid"
        fill
        className="object-contain"
      />
    </div>
  ),
};

export function DownloadsSection({ downloadUrl }: { downloadUrl?: string }) {
  FlaskConical;
  return (
    <section className="py-32 px-4 sm:px-6 lg:px-8 bg-background relative overflow-hidden">
      {/* Background Lighting */}
      <div className="absolute bottom-0 left-1/2 -translate-x-1/2 w-[800px] h-[300px] bg-secondary/10 blur-[120px] rounded-full opacity-30 pointer-events-none" />

      <div className="max-w-7xl mx-auto relative z-10">
        <h2 className="text-4xl md:text-6xl font-bold mb-6 text-center tracking-tight">
          <span className="bg-clip-text text-transparent bg-linear-to-r from-foreground via-foreground/80 to-foreground/40">
            Get it on
          </span>
        </h2>
        <p className="text-lg text-muted-foreground text-center mb-20 max-w-2xl mx-auto">
          Download mpvExtended from your preferred source and start experiencing
          premium playback today.
        </p>

        <div className="grid grid-cols-1 md:grid-cols-3 gap-8">
          {downloadOptions.map((option) => (
            <motion.div
              key={option.id}
              whileHover={{
                scale: 1.05,
                transition: { duration: 0.2 },
              }}
              className="download-card relative overflow-hidden bg-foreground/3 dark:bg-white/2 backdrop-blur-md border border-foreground/5 dark:border-white/5 rounded-3xl p-8 flex flex-col items-center text-center hover:bg-foreground/5 dark:hover:bg-white/4 hover:border-secondary/30 transition-all duration-300 group"
            >
              <div className="absolute inset-0 bg-linear-to-br from-secondary/5 to-transparent opacity-0 group-hover:opacity-100 transition-opacity duration-500" />

              <div className="relative z-10 flex flex-col h-full items-center text-center">
                <div className="mb-6 text-secondary flex justify-center transition-transform duration-500 group-hover:scale-110">
                  {iconMap[option.icon]}
                </div>
                <h3 className="text-2xl font-semibold text-foreground mb-3 ">
                  {option.title}
                </h3>
                <p className="text-muted-foreground leading-relaxed mb-8 grow text-sm">
                  {option.description}
                </p>
                <Button
                  className="w-full h-12 bg-secondary hover:bg-secondary/80 text-secondary-foreground rounded-2xl transition-all duration-300 flex items-center justify-center gap-2 shadow-lg cursor-pointer"
                  onClick={() => {
                    if (option.title === "Preview Builds" && downloadUrl) {
                      window.open(downloadUrl, "_blank");
                    } else {
                      window.open(option.link, "_blank");
                    }
                  }}
                >
                  {option.cta}
                  <ArrowRight className="w-5 h-5 transition-transform group-hover:translate-x-1" />
                </Button>
              </div>
            </motion.div>
          ))}
        </div>
      </div>
    </section>
  );
}
