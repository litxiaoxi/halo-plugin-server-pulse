import { markRaw } from "vue";
import ServerPulseWidget from "@/widgets/ServerPulseWidget.vue";

/**
 * Halo console plugin module.
 *
 * The legacy IIFE loader reads this object from window["server-pulse"], so the
 * default export must stay a plain PluginModule object.
 */
export default {
  components: {},
  routes: [],
  extensionPoints: {
    "console:dashboard:widgets:create": () => [
      {
        id: "server-status",
        component: markRaw(ServerPulseWidget),
        group: "服务器",
        // Halo 2.21 checks `configFormKitSchema.length` before showing the
        // widget config button, so this must be an array rather than a function.
        configFormKitSchema: [
          {
            $formkit: "text",
            name: "title",
            label: "标题",
            placeholder: "留空时使用服务器名称",
          },
          {
            $formkit: "checkbox",
            name: "showUptime",
            label: "显示运行时间",
          },
          {
            $formkit: "checkbox",
            name: "showHost",
            label: "显示硬件信息",
          },
        ],
        defaultConfig: {
          title: "",
          showUptime: true,
          showHost: true,
        },
        defaultSize: {
          w: 6,
          h: 6,
          minW: 4,
          minH: 5,
          maxW: 12,
          maxH: 12,
        },
      },
    ],
  },
};
