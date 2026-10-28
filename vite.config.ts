// Configuração do Vite usando os plugins oficiais do ecossistema TanStack e React.
// O Vite inicia o servidor de desenvolvimento e prepara os arquivos para produção.
import { defineConfig } from "vite";
import { tanstackStart } from "@tanstack/react-start/plugin/vite";
import viteReact from "@vitejs/plugin-react";
import tailwindcss from "@tailwindcss/vite";
import tsconfigPaths from "vite-tsconfig-paths";

export default defineConfig({
  // Cada plugin acrescenta uma capacidade ao processo de desenvolvimento e compilação.
  plugins: [
    tanstackStart({
      // Usa src/server.ts como entrada do servidor para manter o tratamento de erros existente.
      server: { entry: "server" },
    }),
    viteReact(),
    tailwindcss(),
    tsconfigPaths(),
  ],
});
