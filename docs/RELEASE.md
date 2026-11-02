# 发布与验证清单

## 构建

```bash
JAVA_HOME=/path/to/jdk-21 ./gradlew clean build
```

Windows：

```bat
set JAVA_HOME=D:\Develop\jdk\jdk-21
gradlew.bat clean build
```

检查：

```bash
ls build/libs/
jar tf build/libs/*.jar
unzip -p build/libs/*.jar plugin.yaml
```

JAR 根目录应包含 `plugin.yaml`、`logo.png`、`templates/pulse.html`、`static/pulse/**`、`extensions/*.yaml`，以及随插件打包的 OSHI/JNA 类与 native 资源（本项目由 Halo Gradle 插件直接展开到 JAR）。

## 本地 Halo 验证（无 Docker）

1. 下载 Halo 2.27.2 社区版 JAR：

   ```bash
   mkdir -p .tools/halo .tools/halo-data
   curl -L https://dl.halo.run/release/halo-2.27.2.jar -o .tools/halo/halo.jar
   ```

2. 创建 `.tools/halo/application.yaml`：

   ```yaml
   server:
     port: 8090
   spring:
     r2dbc:
       url: r2dbc:h2:file:///D:/path/to/server-pulse/.tools/halo-data/db/halo-next?MODE=MySQL&DB_CLOSE_ON_EXIT=FALSE
       username: admin
       password: 123456
     sql:
       init:
         mode: always
         platform: h2
   halo:
     work-dir: D:/path/to/server-pulse/.tools/halo-data
     external-url: http://localhost:8090
   ```

3. 启动：

   ```bash
   /d/Develop/jdk/jdk-21/bin/java -Dfile.encoding=UTF-8 \
     -jar .tools/halo/halo.jar \
     --spring.config.additional-location=optional:file:.tools/halo/
   ```

4. 打开 `http://localhost:8090` 完成初始化。
5. 将 `build/libs/*.jar` 放入 `.tools/halo-data/plugins/`，重启 Halo，在 Console 中启用 ServerPulse。
6. 验证清单：
   - 未登录 + 默认 `login`：`/pulse` 显示登录引导，API 返回 401。
   - 登录后：指标、历史曲线、主题、刷新间隔正常。
   - 设置切换 `public`：匿名可访问；切回 `login`：匿名恢复 401。
   - 手机 375×812、平板 768×1024、桌面 1440×900 无横向溢出。
   - 页面隐藏时暂停轮询；恢复可见立即刷新。
   - 插件禁用后 `/pulse` 不可用；重新启用后恢复。
   - 重启 Halo 后设置保留。
   - 浏览器控制台无未处理错误，无外部网络请求。
7. 用 Halo 2.21.0 在独立工作目录重复关键路径，确认最低兼容版本。
8. 生成应用市场截图：桌面亮色、桌面暗色、移动端各一张，必须来自真实页面。

## 应用市场资料

- 应用名称：ServerPulse 服务器监控
- 版本：1.0.0
- Halo 兼容范围：`>=2.21.0`
- 许可证：MIT
- 简介：为 Halo 提供独立的服务器监控页面；默认仅登录用户可见。
- 需披露：第三方依赖许可（OSHI Apache-2.0、JNA LGPL-2.1-or-later / Apache-2.0，见 NOTICE）、无遥测/无外发、Docker/cgroup 指标口径。
- 确认 `plugin.yaml` 中的 `homepage`、`repo`、`issues` 指向 `https://github.com/litxiaoxi/halo-plugin-server-pulse`，`author.website` 指向 `https://litx.cn`。
- 应用创建后添加 `metadata.annotations."store.halo.run/app-id": "app-..."`。

## 本次验证记录（本地）

- 日期：2026-11-02
- 插件版本：1.0.0
- Halo 版本：2.27.2、2.21.0（Java 21.0.9）
- 验证结果：
  - Halo 2.27.2：安装、启用、重启后自动启动；`/pulse` 200；匿名 API 401；登录后 API 200；OSHI 正常读取 CPU/内存/磁盘；禁用后 `/pulse` 404，重新启用后恢复。
  - Halo 2.21.0：安装、启用；`/pulse` 200；匿名 API 401；登录后页面与指标正常；静态资源 200。
  - 公开访问模式：匿名 API 200，页面正常；切回仅登录后匿名 401。
  - 设置项：服务器名称、访问模式、显示历史曲线、显示详细硬件信息均生效；`showDetails=false` 时服务端省略 `info`/`runtime`。
  - 页面在 1440×1000、390×844 下无横向溢出；浏览器控制台无错误；无外部域名请求。
- 截图：`docs/screenshots/desktop-light.png`、`desktop-dark.png`、`mobile.png`。
- 制品：`build/libs/server-pulse-1.0.0.jar`
- SHA-256：`fb34d4e6dd610e5af28613908e22ad85196c3cb2b94009f294e16897f96d5f30`

## 记录模板

```text
Commit and tag:
Plugin version:
Halo versions tested:
Java version:
Clean install: pass/fail
Upgrade from: version, pass/fail
Commands run:
Roles and access modes checked:
Known limitations:
Artifact path and SHA-256:
```
