"use client";

import { motion } from "framer-motion";
import { Github } from "lucide-react";
import Image from "next/image";
import { useEffect, useState } from "react";
import {
  type GitHubContributor,
  getRepositoryContributors,
} from "@/lib/github";

export function ContributorsSection() {
  const [contributors, setContributors] = useState<GitHubContributor[]>([]);
  const [loading, setLoading] = useState(true);

  useEffect(() => {
    const fetchContributors = async () => {
      setLoading(true);
      const data = await getRepositoryContributors(50);
      setContributors(data);
      setLoading(false);
    };

    fetchContributors();
  }, []);

  return (
    <section id="contributors" className="py-20 px-4 sm:px-6 lg:px-8 bg-card">
      <div className="max-w-6xl mx-auto">
        <div className="text-center mb-16">
          <h2 className="text-4xl sm:text-5xl font-bold text-foreground mb-4 text-balance">
            Contributors
          </h2>
          <p className="text-lg text-muted-foreground text-balance">
            Amazing people making mpvExtended better every day
          </p>
        </div>

        {loading ? (
          <div className="flex justify-center items-center py-20">
            <div className="text-muted-foreground">Loading contributors...</div>
          </div>
        ) : (
          <div className="grid grid-cols-2 sm:grid-cols-3 md:grid-cols-4 lg:grid-cols-5 gap-4">
            {contributors.slice(0, 20).map((contributor) => (
              <motion.a
                key={contributor.login}
                whileHover={{ y: -10 }}
                transition={{ duration: 0.3 }}
                href={contributor.html_url}
                target="_blank"
                rel="noopener noreferrer"
                className="contributor-card group block h-full"
              >
                <div className="bg-background border border-border rounded-2xl p-6 flex flex-col items-center gap-4 hover:border-primary transition-all duration-300 h-full">
                  <div className="relative w-16 h-16 rounded-full border-2 border-primary group-hover:scale-110 transition-transform duration-300 overflow-hidden">
                    <Image
                      src={contributor.avatar_url || "/placeholder.svg"}
                      alt={contributor.login}
                      fill
                      sizes="64px"
                      className="object-cover"
                    />
                  </div>
                  <div className="text-center grow">
                    <p className="font-semibold text-foreground text-sm line-clamp-2">
                      {contributor.login}
                    </p>
                    <p className="text-xs text-muted-foreground mt-1">
                      {contributor.contributions} commits
                    </p>
                  </div>
                  <Github className="w-4 h-4 text-primary opacity-0 group-hover:opacity-100 transition-opacity duration-300" />
                </div>
              </motion.a>
            ))}
          </div>
        )}

        <div className="text-center mt-12">
          <a
            href="https://github.com/marlboro-advance/mpvEx/graphs/contributors"
            target="_blank"
            rel="noopener noreferrer"
            className="inline-flex items-center gap-2 px-6 py-3 bg-primary hover:bg-primary/90 text-primary-foreground rounded-full transition-all duration-300 hover:shadow-lg"
          >
            View all contributors
            <Github className="w-4 h-4" />
          </a>
        </div>
      </div>
    </section>
  );
}
