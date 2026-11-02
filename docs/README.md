# TangsRail Decoration (TRDM)

> 面向 **Minecraft 1.20.1 + MTR 3** 的轨道交通装饰模组：天津风格的站台屏蔽门、站名牌和 PIS 乘客信息屏。

TRDM 是在 [MTR](https://www.curseforge.com/minecraft/mc-mods/minecraft-transit-railway) 之上做的一层装饰扩展：
方块、渲染、界面都是自己写的，但**站台、车站、线路、列车到站数据全部读 MTR 的**。
所以用法上就是「先用 MTR 把线路和站台摆好，再用 TRDM 把车站装出来」。

## 功能一览

| 分类 | 内容 |
|---|---|
| **屏蔽门** | 门扇 / 玻璃 / 端头玻璃 / 门楣 / 端门（可旋开、合并后整段当一扇大门）；刷子合并、外观设置 |
| **站名牌** | 站名牌、站外大站名牌、悬挂式站外站名牌（站名 + 线路色带 + 换乘标志 + 出口字母） |
| **PIS** | 16:9 吊挂乘客信息屏：实时到站数据、跑马灯、由**资源包驱动的图片 / 动图 / 视频广告** |
| **集控** | 屏蔽门集控装置：选站台、手动保持开门、屏蔽门隔离 |

## 快速开始

1. 按 [安装与依赖](install.md) 装好 MTR 3.6.x 和 Architectury API；
2. 把 `trdm-*.jar` 丢进 `mods/`（**客户端和服务端都要装**）；
3. 创造模式物品栏里「TangsRail 装饰」分页能找到全部物品。

## 文档目录

- [安装与依赖](install.md)
- 使用
  - [方块与屏蔽门](blocks.md)
  - [站名牌](signs.md)
  - [PIS 乘客信息屏](pis.md)
  - [集控装置与通用设置](control.md)
- 资源包 / 附属资源
  - [资源包总览与可覆盖清单](resourcepack.md)
  - [PIS 广告资源包](resourcepack-pis-ads.md)
  - [标志牌贴图与预设](resourcepack-signs.md)
- [开发与构建](dev.md)

## 许可

MIT。站名牌/指示牌部分素材由 **Baiyun_Hiyaku** 提供，PIS 贴图来自 JCM 的 TJPIS 资源包，
中文渲染使用 OFL 授权的思源黑体（详见仓库里的 `THIRD-PARTY-NOTICES.md`）。
