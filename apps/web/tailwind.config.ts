import type { Config } from "tailwindcss";

const config: Config = {
  darkMode: ["class"],
  content: [
    "./app/**/*.{ts,tsx}",
    "./components/**/*.{ts,tsx}",
    "./features/**/*.{ts,tsx}",
    "./lib/**/*.{ts,tsx}"
  ],
  theme: {
    extend: {
      colors: {
        background: "hsl(var(--background))",
        foreground: "hsl(var(--foreground))",
        muted: "hsl(var(--muted))",
        "muted-foreground": "hsl(var(--muted-foreground))",
        border: "hsl(var(--border))",
        input: "hsl(var(--input))",
        ring: "hsl(var(--ring))",
        card: "hsl(var(--card))",
        "card-foreground": "hsl(var(--card-foreground))",
        primary: "hsl(var(--primary))",
        "primary-foreground": "hsl(var(--primary-foreground))",
        accent: "hsl(var(--accent))",
        "accent-foreground": "hsl(var(--accent-foreground))",
        success: "hsl(var(--success))",
        danger: "hsl(var(--danger))",
        warning: "hsl(var(--warning))"
      },
      fontFamily: {
        sans: ["var(--font-sans)", "Inter", "ui-sans-serif", "system-ui", "sans-serif"],
        mono: ["var(--font-mono)", "SFMono-Regular", "ui-monospace", "Menlo", "monospace"]
      },
      fontSize: {
        "display-lg": ["3.5rem", { lineHeight: "1", fontWeight: "650" }],
        "display-md": ["2.5rem", { lineHeight: "1.08", fontWeight: "650" }],
        "title-lg": ["1.5rem", { lineHeight: "1.25", fontWeight: "620" }],
        "body-sm": ["0.875rem", { lineHeight: "1.55" }],
        micro: ["0.6875rem", { lineHeight: "1.35", letterSpacing: "0" }]
      },
      spacing: {
        18: "4.5rem",
        22: "5.5rem",
        30: "7.5rem"
      },
      borderRadius: {
        xl: "var(--radius-xl)",
        lg: "var(--radius-lg)",
        md: "var(--radius-md)",
        sm: "var(--radius-sm)"
      },
      boxShadow: {
        glow: "0 0 0 1px hsl(var(--border) / 0.75), 0 24px 80px hsl(220 70% 2% / 0.55)",
        "glow-cyan": "0 0 0 1px hsl(190 95% 58% / 0.22), 0 18px 70px hsl(190 95% 35% / 0.16)"
      },
      keyframes: {
        "aurora-drift": {
          "0%, 100%": { transform: "translate3d(-8%, -4%, 0) scale(1)" },
          "50%": { transform: "translate3d(8%, 4%, 0) scale(1.08)" }
        },
        "fade-up": {
          "0%": { opacity: "0", transform: "translateY(10px)" },
          "100%": { opacity: "1", transform: "translateY(0)" }
        },
        shimmer: {
          "0%": { backgroundPosition: "-200% 0" },
          "100%": { backgroundPosition: "200% 0" }
        },
        ticker: {
          "0%": { transform: "translateX(0)" },
          "100%": { transform: "translateX(-50%)" }
        },
        pulseGlow: {
          "0%, 100%": { boxShadow: "0 0 0 0 hsl(var(--primary) / 0)" },
          "50%": { boxShadow: "0 0 0 4px hsl(var(--primary) / 0.14)" }
        }
      },
      animation: {
        "aurora-drift": "aurora-drift 18s ease-in-out infinite",
        "fade-up": "fade-up 420ms ease-out both",
        shimmer: "shimmer 1.8s linear infinite",
        ticker: "ticker 34s linear infinite",
        "pulse-glow": "pulseGlow 1.8s ease-in-out infinite"
      },
      backdropBlur: {
        xs: "2px"
      }
    }
  },
  plugins: []
};

export default config;
