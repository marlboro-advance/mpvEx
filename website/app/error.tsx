"use client";

import { motion, type Variants } from "framer-motion";
import { RefreshCcw } from "lucide-react";
import { useEffect } from "react";
import { Button } from "@/components/ui/button";

export default function ErrorPage({
  error,
  reset,
}: {
  error: Error & { digest?: string };
  reset: () => void;
}) {
  useEffect(() => {
    // Log the error to an error reporting service
    console.error(error);
  }, [error]);

  const containerVariants: Variants = {
    hidden: { opacity: 0 },
    visible: {
      opacity: 1,
      transition: {
        staggerChildren: 0.1,
      },
    },
  };

  const itemVariants: Variants = {
    hidden: { opacity: 0, y: 20 },
    visible: {
      opacity: 1,
      y: 0,
      transition: { duration: 0.5, ease: "easeOut" },
    },
  };

  return (
    <motion.div
      variants={containerVariants}
      initial="hidden"
      animate="visible"
      className="error-container min-h-screen flex flex-col items-center justify-center bg-background text-foreground p-4 text-center"
    >
      <motion.h2 variants={itemVariants} className="text-4xl font-bold mb-4 text-destructive">
        Something went wrong!
      </motion.h2>
      <motion.p variants={itemVariants} className="text-muted-foreground text-lg mb-8 max-w-md">
        We encountered an unexpected error. Please try again later.
      </motion.p>
      <motion.div variants={itemVariants}>
        <Button
          onClick={reset}
          className="bg-primary hover:bg-primary/90 text-primary-foreground rounded-full px-8 py-6 text-lg shadow-lg hover:shadow-xl transition-all"
        >
          <RefreshCcw className="mr-2 w-5 h-5" />
          Try again
        </Button>
      </motion.div>
      {error.digest && (
        <motion.p variants={itemVariants} className="mt-8 text-xs text-muted-foreground font-mono">
          Error ID: {error.digest}
        </motion.p>
      )}
    </motion.div>
  );
}
