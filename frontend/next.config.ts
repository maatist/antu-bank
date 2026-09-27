import type { NextConfig } from "next";
import createNextIntlPlugin from "next-intl/plugin";

// Plugin de next-intl: enlaza la configuración por request (idioma + mensajes) con el build.
// Enfoque "sin ruteo por locale": el idioma se resuelve por cookie (ver src/i18n/request.ts).
const withNextIntl = createNextIntlPlugin("./src/i18n/request.ts");

// Configuración de Next.js. El BFF GraphQL (10.2) y la i18n es/en (10.3) ya están integrados.
const nextConfig: NextConfig = {
  reactStrictMode: true,
};

export default withNextIntl(nextConfig);
