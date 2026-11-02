# ServerPulse 服务器监控（Halo 插件）

为 Halo 提供独立的服务器监控页面，在 Halo 进程内直接读取服务器指标，不需要额外 Agent、独立服务或数据库。

仓库：<https://github.com/litxiaoxi/halo-plugin-server-pulse>

![ServerPulse 监控面板](docs/screenshots/desktop-light.png)

## 功能

- CPU、内存、磁盘使用率与系统运行时间。
- CPU 型号、核心数、频率、架构，操作系统、内存类型、磁盘型号、Swap、进程数。
- 最近约 2 分钟的 CPU / 内存 / 磁盘历史曲线。
- JVM 运行时间与堆内存使用情况。
- 亮色、暗色、跟随系统三种主题。
- 响应式布局，适配桌面、平板和手机。
- Halo 设置表单：服务器名称、默认主题、刷新间隔、访问模式、历史曲线、详细硬件信息。
- 默认仅登录用户可见；公开访问需要显式开启。
- OSHI 不可用时自动降级为 JVM 基础指标，不影响 Halo 启动。

## 环境要求

- Halo `>= 2.21.0`
- Java 21（Halo 2.21+ 的运行时要求）

## 安装

1. 在 Halo Console 的「插件」页面安装插件 JAR。
2. 启用插件。
3. 打开 `https://你的站点/pulse`。
4. 如需站点导航入口，在 Halo「菜单」中添加一个指向 `/pulse` 的自定义链接。

## 设置

插件详情页提供以下设置：

| 设置 | 默认值 | 说明 |
| --- | --- | --- |
| 服务器名称 | 空 | 留空时使用站点标题。 |
| 默认主题 | 跟随系统 | 访客仍可在页面右上角切换。 |
| 刷新间隔 | 3 秒 | 允许 1–30 秒；页面隐藏时暂停轮询。 |
| 访问权限 | 仅登录用户 | 可选公开访问；公开后会向所有访客展示服务器概况。 |
| 显示历史曲线 | 开启 | 展示最近约 2 分钟的三项指标趋势。 |
| 显示详细硬件信息 | 开启 | 关闭后服务端也会省略 CPU 型号、系统、磁盘、进程数等详细信息。 |

### 访问控制

- **仅登录用户（默认）**：匿名访客打开 `/pulse` 只能看到登录引导，监控 API 返回 401。
- **公开访问**：任何访客都能看到监控数据。请确认你接受 CPU 型号、操作系统、磁盘等服务器信息被公开。

插件只申请 `api.serverpulse.halo.run` 下 `metrics` 资源的只读权限，没有写操作、数据库或外部服务。

## 接口

页面通过只读接口获取数据：

```text
GET /apis/api.serverpulse.halo.run/v1alpha1/metrics
```

匿名访问时仍受 Halo 权限和插件访问设置控制；默认返回 401，公开模式开启后才允许匿名读取。

## 指标口径

- 指标从 Halo 所在进程/主机读取。Halo 使用 Docker 时，CPU、内存、进程数等可能反映宿主机或容器可见值，具体取决于运行环境和 cgroup 配置。
- 磁盘使用率优先取系统盘（Windows `SystemDrive`）或根分区 `/`；物理磁盘总量单独展示，避免重复累加多个挂载点。
- 历史数据只保存在内存中，Halo 重启或插件重载后清空。
- 如果 OSHI/JNA 在当前部署环境不可用，页面会显示“部分硬件信息不可用”，并使用 JVM 基础指标降级。

## 主题覆盖

`/pulse` 默认使用插件内置的独立模板 `templates/pulse.html`。如果当前主题提供了同名 `pulse.html`，会优先使用主题模板。传给模板的稳定字段包括 `pulseTheme`、`apiBase`、`pluginName`、`pluginVersion` 和 `_templateId`（`plugin:server-pulse:pulse`）。主题覆盖时应保留这些字段的语义。

## 隐私

插件不收集、不上传、不向第三方发送任何数据；不加载 CDN、外部字体、统计脚本或广告。所有前端资源都随插件 JAR 分发。详见 [隐私说明](docs/PRIVACY.md)。

## 开发

需要 JDK 21。Windows 本机可使用：

```bash
set JAVA_HOME=D:\Develop\jdk\jdk-21
gradlew.bat clean build
```

或 Git Bash / macOS / Linux：

```bash
JAVA_HOME=/path/to/jdk-21 ./gradlew clean build
```

构建产物位于 `build/libs/`。本项目没有 UI 子工程，不需要 Node.js 或 pnpm。

### 本地 Halo 联调

本项目没有 Docker 依赖。可使用 Halo 官方 JAR + H2 在本机验证，步骤见 [docs/RELEASE.md](docs/RELEASE.md)。

## 兼容性

- 使用 Halo 2.21.0 API 编译，声明兼容 `>= 2.21.0`。
- 发布前应在 Halo 2.21.x 与当前 Halo 2.27.x 上分别验证安装、启用、页面、权限、禁用和重启。

## 许可

本项目为原创项目，代码以 MIT 许可证发布。第三方依赖 OSHI（Apache-2.0）和 JNA（LGPL-2.1-or-later / Apache-2.0）的许可证与版权声明见 [NOTICE](NOTICE)，依赖说明见 [docs/ATTRIBUTION.md](docs/ATTRIBUTION.md)。

