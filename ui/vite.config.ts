import { fileURLToPath, URL } from "node:url";
import vue from "@vitejs/plugin-vue";
import { defineConfig } from "vite";

const PLUGIN_NAME = "server-pulse";
const GLOBAL_NAME = "__SERVER_PULSE__";

export default defineConfig(({ mode }) => {
  const development = mode === "development";

  return {
    plugins: [vue()],
    base: "./",
    resolve: {
      alias: {
        "@": fileURLToPath(new URL("./src", import.meta.url)),
      },
    },
    build: {
      // Dev builds land directly in the classpath used by `runHalo`; production
      // builds stay in ui/build/dist and Gradle copies them into the plugin JAR.
      outDir: development ? "../build/resources/main/console" : "build/dist",
      emptyOutDir: true,
      lib: {
        entry: "src/index.ts",
        name: GLOBAL_NAME,
        formats: ["iife"],
        fileName: () => "main.js",
        cssFileName: "style",
      },
      rollupOptions: {
        external: ["vue"],
        output: {
          globals: { vue: "Vue" },
          // Halo's legacy provider loader reads window[plugin.metadata.name].
          footer: `window[${JSON.stringify(PLUGIN_NAME)}] = (${GLOBAL_NAME} && ${GLOBAL_NAME}.default) || ${GLOBAL_NAME};`,
        },
      },
    },
  };
});
