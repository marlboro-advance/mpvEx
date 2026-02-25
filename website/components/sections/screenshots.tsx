"use client";

import { motion } from "framer-motion";
import Image from "next/image";

const screenshots = [
  {
    id: 1,
    title: "File Explorer",
    image: "/IMG-20251120-WA0004.jpg",
  },
  {
    id: 2,
    title: "Video Player",
    image: "/IMG-20251120-WA0002.jpg",
  },
  {
    id: 3,
    title: "Settings",
    image: "/IMG-20251120-WA0003.jpg",
  },
  {
    id: 4,
    title: "Playback",
    image: "/IMG-20251120-WA0005.jpg",
  },
  {
    id: 5,
    title: "Picture-in-Picture",
    image: "/IMG-20251120-WA0006.jpg",
  },
  {
    id: 6,
    title: "Media Controls",
    image: "/IMG-20251120-WA0008.jpg",
  },
];

export function ScreenshotsSection() {
  return (
    <section
      id="screenshots"
      className="py-16 px-4 sm:px-6 lg:px-8 bg-background relative overflow-hidden"
    >
      {/* Background Lighting */}
      <div className="absolute top-[20%] left-1/2 -translate-x-1/2 w-[600px] h-[300px] bg-primary/20 blur-[100px] rounded-full opacity-20 pointer-events-none" />

      <div className="max-w-7xl mx-auto relative z-10">
        <div className="text-center mb-10">
          <h2 className="text-4xl md:text-5xl font-bold mb-6 tracking-tight">
            <span className="bg-clip-text text-transparent bg-linear-to-r from-foreground via-foreground/80 to-foreground/50">
              Screenshots
            </span>
          </h2>
          <p className="text-lg text-muted-foreground max-w-2xl mx-auto">
            Modern Material3 design that adapts to your device.
          </p>
        </div>

        <div className="flex justify-start overflow-x-auto pb-12 pt-12 pl-4 md:pl-32 pr-4 md:pr-32 scrollbar-hide snap-x snap-proximity">
          <div className="flex flex-nowrap items-center gap-6 md:gap-12 w-max">
            {screenshots.map((screenshot) => (
              <motion.div
                key={screenshot.id}
                whileHover={{
                  scale: 1.08,
                  zIndex: 20,
                  transition: { duration: 0.2 },
                }}
                className="relative shrink-0 w-[160px] md:w-[200px] aspect-9/19 rounded-4xl overflow-hidden border-4 border-foreground/5 dark:border-white/5 bg-black shadow-2xl cursor-pointer transition-all duration-200 group snap-center"
              >
                {/* Phone bezel gloss */}
                <div className="absolute inset-0 pointer-events-none z-20 rounded-4xl ring-1 ring-foreground/10 dark:ring-white/10 shadow-[inset_0_0_20px_rgba(0,0,0,0.05)] dark:shadow-[inset_0_0_20px_rgba(255,255,255,0.05)]" />

                <div className="absolute inset-0 z-0">
                  <Image
                    src={screenshot.image}
                    alt={screenshot.title}
                    fill
                    className="object-cover transition-transform duration-300 group-hover:scale-105"
                    sizes="(max-width: 768px) 160px, 200px"
                  />

                  {/* Subtle gradient overlay at bottom for depth */}
                  <div className="absolute inset-x-0 bottom-0 h-1/4 bg-linear-to-t from-black/40 to-transparent opacity-60 transition-opacity" />
                </div>
              </motion.div>
            ))}
          </div>
        </div>
      </div>
    </section>
  );
}
