# 第三方依赖与许可

ServerPulse 为原创项目。

## 第三方依赖

- OSHI（`com.github.oshi:oshi-core`）— Apache-2.0，用于读取 CPU、内存、磁盘和操作系统指标。
- JNA / JNA Platform（`net.java.dev.jna:jna`、`net.java.dev.jna:jna-platform`）— LGPL-2.1-or-later 或 Apache-2.0，OSHI 的本地系统调用依赖。

完整许可证文本和版权声明见项目根目录 [NOTICE](../NOTICE)。本项目自身代码以 MIT 许可证发布，见 [LICENSE](../LICENSE)。

## 维护计划

- 跟进 Halo 2.x API 变化，至少验证 2.21.x 与当前 2.27.x。
- 处理 OSHI/JNA 在不同平台与容器环境下的兼容性。
- 后续可评估 cgroup v2 口径、管理员专属访问模式、进程列表等增强。
