/// <reference types="vite/client" />

import type { DefineComponent } from "vue";

declare module "vue" {
  export interface GlobalComponents {
    // Registered globally by Halo's core dashboard module in 2.21 and 2.27.
    WidgetCard: DefineComponent<{
      title?: string;
      bodyClass?: unknown;
    }>;
  }
}

export {};
