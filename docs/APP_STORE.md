# Halo 应用市场上架文案

> 提交前请确认仓库、作者、Issue 等信息和截图均为真实内容。

## 基本信息

- 名称：ServerPulse 服务器监控
- 类型：插件
- 版本：1.3.0
- Halo 兼容范围：`>=2.21.0`
- 许可证：MIT
- 简介：为 Halo 提供独立的服务器监控页面与控制台仪表盘部件，展示 CPU、内存、磁盘、运行时间与硬件信息。
- 仓库：`https://github.com/litxiaoxi/halo-plugin-server-pulse`
- 问题反馈：`https://github.com/litxiaoxi/halo-plugin-server-pulse/issues`
- 主页：`https://github.com/litxiaoxi/halo-plugin-server-pulse#readme`

## 详情

ServerPulse 把服务器状态做成一个干净、独立的 Halo 前台页面：

- 实时查看 CPU、内存、磁盘使用率和系统运行时间。
- 查看 CPU 型号、核心数、频率、操作系统、内存类型、磁盘、Swap、进程数。
- 最近约 2 分钟的 CPU / 内存 / 磁盘趋势曲线。
- 亮色、暗色和跟随系统主题，适配桌面与手机。
- 在 Halo 控制台 `/console/dashboard` 添加 ServerPulse 仪表盘部件，直接查看服务器概况。
- 直接在 Halo 插件设置中配置服务器名称、刷新间隔、访问权限和展示项。
- 支持仅登录用户、公开访问和指定用户访问；默认为仅登录用户。
- 不依赖额外 Agent、独立服务、数据库或外部 CDN。

## 访问与隐私

插件默认只允许已登录的 Halo 用户查看监控数据。管理员可以在插件设置中开启公开访问，或改为指定用户访问，只允许选中的 Halo 用户查看。公开访问开启后任何访客都能看到服务器概况，请谨慎使用。

插件不收集、不上传、不向第三方发送数据，不包含遥测、广告或远程配置。所有前端资源随插件 JAR 分发。

## 指标说明

指标由 OSHI 从 Halo 所在进程/主机读取。Halo 运行在 Docker 时，CPU、内存、进程数等可能反映宿主机或容器可见值，具体取决于运行环境和 cgroup 配置。磁盘使用率优先按系统盘/根分区计算，避免多个挂载点重复累加。历史数据只保存在内存中，重启后清空。

如果 OSHI/JNA 在部署环境不可用，插件会自动降级为 JVM 基础指标，并在页面提示“部分硬件信息不可用”。

## 截图顺序

1. `screenshots/desktop-light.png`：桌面亮色主界面。
2. `screenshots/desktop-dark.png`：桌面暗色主界面。
3. `screenshots/mobile.png`：移动端界面。
4. `screenshots/dashboard-widget.png`：控制台仪表盘中的 ServerPulse 部件。
5. 可选：`screenshots/login-required.png`、`screenshots/public-anonymous.png`。

## 版本说明（1.3.0）

1.3.0：重做 `/pulse` 监控页视觉与交互，指标采用紫、蓝、琥珀三色体系，新增信号轨迹装饰、发光仪表环与更清晰的仪表排版；默认刷新间隔调整为 2 秒（服务端 1 秒采样，历史曲线保留 2 分钟），优化悬停反馈与趋势图表现，并修复反复刷新导致的页面累积上移等问题。1.2.0：新增 Halo 控制台仪表盘部件，可在 `/console/dashboard` 点击右上角「设置」→「添加部件」→「小部件中心」添加，直接查看 CPU、内存、磁盘、运行时间与硬件摘要；复用现有访问策略，并支持部件标题与展示项配置。此前版本包含服务器指标面板、历史曲线、亮暗主题、Halo 设置、登录/公开访问控制、OSHI 指标与 JVM 降级。

## 原创声明与第三方依赖

ServerPulse 为原创项目。本项目自身代码以 MIT 许可证发布；第三方依赖许可（OSHI Apache-2.0、JNA LGPL-2.1-or-later / Apache-2.0）见项目根目录 `NOTICE`。
