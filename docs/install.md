# 安装与依赖

## 环境要求

| 项目 | 要求 |
|---|---|
| Minecraft | **1.20.1** |
| 模组加载器 | Fabric Loader **0.19.3 或更新** |
| Fabric API | 必需（1.20.1 版本） |
| **[MTR](https://www.curseforge.com/minecraft/mc-mods/minecraft-transit-railway)** | **3.6.x**（开发时用的是 `MTR-fabric-1.20.1-3.6.3`） |
| Architectury API | **9.2.14（Fabric 1.20.1）** —— MTR 的运行时依赖，但它没写在 `fabric.mod.json` 里，**必须自己单独装**，否则客户端加载 MTR 入口时会崩 |
| Java | **17** |

## 安装步骤

1. 装好 Fabric 1.20.1 的加载器；
2. 把下面四个 jar 放进 `.minecraft/mods/`（服务端同理放 `mods/`）：

   - `fabric-api-*.jar`
   - `MTR-fabric-1.20.1-3.6.3.jar`
   - `architectury-9.2.14-fabric.jar`
   - `trdm-*.jar`

3. 启动游戏。日志里出现 `TangsRail Decoration initialized` 就是加载成功。

## 客户端 / 服务端

- **服务端必须装**：方块、方块实体、集控装置的网络通道都在服务端；
- **客户端必须装**：所有招牌、PIS、屏蔽门、端门的渲染都在客户端（服务端不装会缺注册表，直接连不上）。

## 常见问题

**进不去存档 / 启动报缺少依赖**
检查 `architectury-9.2.14-fabric.jar` 是否在 mods 目录里。MTR 3.x 的 Fabric 版本没有声明这个依赖，
但它的客户端入口引用了 `dev.architectury.event.events.client.ClientTickEvent`。

**版本不匹配**
本模组是按 MTR **3.6.x** 做的（方块状态、渲染器接口都跟 MTR 内部实现对齐）。
MTR 4 是另一套代码，不能混用。

**PIS 广告不显示**
广告由资源包提供，见 [PIS 广告资源包](resourcepack-pis-ads.md)。
