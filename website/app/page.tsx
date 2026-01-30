/**
 * @file page.tsx
 * @description Home page component acting as the main landing page.
 * Composes various sections like Hero, Features, Downloads, etc.
 * @module app/page
 */

import { ContributorsSection } from "@/components/sections/contributors";
import { DownloadsSection } from "@/components/sections/downloads";
import { FeaturesSection } from "@/components/sections/features";
import { Footer } from "@/components/sections/footer";
import { Header } from "@/components/sections/header";
import { HeroSection } from "@/components/sections/hero";
import { ScreenshotsSection } from "@/components/sections/screenshots";
import { getLatestRelease } from "@/lib/github";
import { siteConfig } from "@/lib/site";

export default async function Home() {
  const latestRelease = await getLatestRelease();
  const version = latestRelease?.tag_name || siteConfig.version;

  return (
    <main className="bg-background text-foreground">
      <Header />
      <HeroSection version={version} />
      <FeaturesSection />
      <DownloadsSection />
      <ScreenshotsSection />
      <ContributorsSection />
      <Footer />
    </main>
  );
}
