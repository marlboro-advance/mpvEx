/**
 * @file theme-provider.tsx
 * @description Theme provider component wrapping the application with next-themes.
 * Enables dark/light mode switching with hydration mismatch handling.
 * @module components/providers/theme-provider
 */

"use client";

import { ThemeProvider as NextThemesProvider, type ThemeProviderProps } from "next-themes";

export function ThemeProvider({ children, ...props }: ThemeProviderProps) {
  return <NextThemesProvider {...props}>{children}</NextThemesProvider>;
}
