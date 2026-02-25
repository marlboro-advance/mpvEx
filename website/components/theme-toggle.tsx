import { motion } from "framer-motion";
import { Moon, Sun } from "lucide-react";
import { useTheme } from "next-themes";
import * as React from "react";
import { Button } from "@/components/ui/button";

export function ThemeToggle() {
  const { setTheme, resolvedTheme } = useTheme();
  const [mounted, setMounted] = React.useState(false);

  React.useEffect(() => {
    setMounted(true);
  }, []);

  if (!mounted) {
    return (
      <Button variant="ghost" size="icon" className="w-9 h-9 opacity-0">
        <span className="sr-only">Toggle theme</span>
      </Button>
    );
  }

  return (
    <Button
      variant="ghost"
      size="icon"
      className="rounded-2xl w-10 h-10 bg-foreground/5 hover:bg-foreground/10 transition-all active:scale-95 relative overflow-hidden flex items-center justify-center font-bold"
      onClick={() => setTheme(resolvedTheme === "dark" ? "light" : "dark")}
    >
      <motion.div
        initial={false}
        animate={{
          scale: resolvedTheme === "dark" ? 0 : 1,
          rotate: resolvedTheme === "dark" ? -90 : 0,
          opacity: resolvedTheme === "dark" ? 0 : 1,
        }}
        transition={{ type: "spring", stiffness: 300, damping: 20 }}
        className="absolute inset-0 flex items-center justify-center"
      >
        <Sun className="h-5 w-5 text-orange-500 fill-orange-500 drop-shadow-[0_0_10px_rgba(249,115,22,0.5)]" />
      </motion.div>
      <motion.div
        initial={false}
        animate={{
          scale: resolvedTheme === "dark" ? 1 : 0,
          rotate: resolvedTheme === "dark" ? 0 : 90,
          opacity: resolvedTheme === "dark" ? 1 : 0,
        }}
        transition={{ type: "spring", stiffness: 300, damping: 20 }}
        className="absolute inset-0 flex items-center justify-center"
      >
        <Moon className="h-5 w-5 text-blue-400 fill-blue-400 drop-shadow-[0_0_10px_rgba(96,165,250,0.5)]" />
      </motion.div>
      <span className="sr-only">Toggle theme</span>
    </Button>
  );
}
