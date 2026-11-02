# 开发与构建

## 环境

| 项目 | 值 |
|---|---|
| Minecraft | 1.20.1 |
| 平台 | Fabric（`net.fabricmc.fabric-loom-remap` 1.17.19） |
| 映射 | 官方 Mojang 映射（mojmap） |
| Java | 17（`java { toolchain { languageVersion = 17 } }`，foojay 自动拉 JDK） |
| Gradle wrapper | 9.5.x |

## 构建

```bash
./gradlew build          # 产物在 build/libs/trdm-DEV_0.1.jar
./gradlew runClient      # 起测试客户端
./gradlew runServer      # 起测试服务端（首次要同意 eula）
```

## 本地依赖（不进仓库）

MTR 和 Architectury 的 jar 走 `local.properties`（已在 `.gitignore` 里，
模板见 `local.properties.example`）：

```properties
mtr_jar=D:/path/to/MTR-fabric-1.20.1-3.6.3.jar
architectury_jar=D:/path/to/architectury-9.2.14-fabric.jar
```

构建脚本里 `requireJar(...)` 找不到会直接报错并提示该填哪个键。

## 目录约定

```
src/main/java/top/xiaotang27/trdm/
├── block/     方块（PSD 门/玻璃/门楣/端门、牌子、PIS、集控）
├── item/      物品（PSD 放置物品、大站名牌物品）
├── render/    客户端渲染（方块实体渲染器、贴图烘焙）
├── client/    客户端界面
├── control/   屏蔽门控制逻辑
├── net/       自定义网络通道（C2S）
└── tools/     一次性离线工具（预览图、贴图导入、类签名检查）
```

## 一次性工具任务

都是 `JavaExec`，只在需要时手动跑：

| 任务 | 用途 |
|---|---|
| `makeSamplePack` | 生成 PIS 广告示例资源包（`build/sample-pack/trdm-pis-sample`） |
| `importJcmAssets` | 把 JCM/TJPIS 资源包里的 PIS 贴图导入本模组 |
| `importPisFont` | 把思源黑体复制进 `assets/trdm/font/` |
| `preview*`（`previewPis` / `previewBand` / `previewRouteMap` / `previewHeaderPanel` …） | 离线渲染各类版面预览图到 `build/preview/` |
| `inspectClass` / `classRefs` / `jarDump` | 打印 MTR 类签名、成员引用、导出 jar 内资源 |

## 开发文档

面向实现细节的说明（需求约定、状态数、渲染约定等）放在 **`docs_fordev/`**：

- `docs_fordev/end-gate-spec.md` —— 端门的需求与实现约定

面向玩家 / 服主的文档就是当前这个 `docs/` 站点。

## 提交约定

一次逻辑改动一个 commit（`git commit -s`，中文描述标题 + 分段说明改了什么、为什么），
构建通过再提交。
