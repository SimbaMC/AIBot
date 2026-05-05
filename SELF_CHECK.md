# NeoForge 1.21.1 迁移自检报告（第一轮）

日期：2026-05-05（UTC）

## 执行的检查

1. `./gradlew compileJava --no-daemon`
   - 失败：`Unsupported class file major version 69`
   - 原因：当前默认 JDK 是 25，Gradle 8.8 / Groovy 在该环境下触发兼容性问题。

2. `JAVA_HOME=~/.local/share/mise/installs/java/21.0.2 PATH=~/.local/share/mise/installs/java/21.0.2/bin:$PATH ./gradlew compileJava --no-daemon`
   - 进入下一阶段，但失败于依赖下载：
   - `Could not GET https://maven.neoforged.net/releases/.../neoform-runtime-1.0.13.pom`（HTTP 403）

## 当前结论

- 工程**未进入 Java 源码编译阶段**（尚未拿到 Minecraft/NeoForge 工具链依赖），因此当前无法输出完整的 Java 编译报错清单。
- 迁移问题至少包含两层：
  1. 本地构建环境 JDK 版本不匹配（应固定 JDK 21）。
  2. NeoForge Maven 依赖访问失败（403），需处理仓库访问/镜像/网络策略。

## 建议的下一步

1. 在开发机固定 JDK 21（建议通过 `JAVA_HOME` 或构建工具配置保证）。
2. 先确认 `https://maven.neoforged.net/releases/` 可访问；若受限，配置可用镜像或代理。
3. 依赖可下载后，重新运行：
   - `./gradlew --refresh-dependencies compileJava`
4. 拿到**真实编译错误**后，再分批修复 Forge -> NeoForge API 迁移点（事件总线、注册、网络包、客户端渲染/GUI 相关 API）。

