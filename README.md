# TangsRail Decoration (TRDM)

基于 [Minecraft Transit Railway 3.x](https://www.curseforge.com/minecraft/mc-mods/minecraft-transit-railway) 的
天津轨道交通（津轨）风格装饰模组，Fabric 1.20.1。

> 本模组的代码与部分美术素材使用了生成式人工智能（AIGC）工具参与制作。

## 内容

- **站台门**：站台门 / 玻璃 / 端头玻璃 / 门楣（吊版 + 玻璃装饰带 + 站名吊板）
- **站名牌**：站名牌、站外站名牌、悬挂式站外站名牌（站名中英等宽、换乘线路标志、出口 / 无障碍电梯牌）
- **PIS 乘客信息屏**：2 格多块的乘客信息屏 + 显示屏柱，跑马灯、广告图 / GIF / MP4 播放
- **屏蔽门集控装置**：按本站站台远程控制站台门，可「隔离」某站台使其只受本装置控制
- **通用设置**：`/trdm config`、快捷键、Mod Menu 三种入口；切换 TRDM 字体、开关 PIS 视频播放

## 依赖

| 依赖 | 版本 | 说明 |
|---|---|---|
| Minecraft | 1.20.1 | |
| Fabric Loader | 0.19.3+ | |
| Fabric API | 0.92.11+1.20.1 | |
| Minecraft Transit Railway | 3.6.3 | mod id `mtr`，必须 |
| Architectury API | 9.2.14 | MTR 的运行时依赖，必须 |
| Mod Menu | 7.2.2+ | 可选，装了才有模组列表入口 |

## 构建

前置：**JDK 21**（fabric-loom 1.17.19 要求构建 JVM ≥ 21，但编译目标是 Java 17）。

构建 JVM 三种指定方式，任选其一：
1. 设置 `JAVA_HOME` 指向 JDK 21；
2. 在用户级 `~/.gradle/gradle.properties` 写 `org.gradle.java.home=<JDK 21 路径>`；
3. 在项目里临时加同名键（注意别提交）。

1. 准备两个本地 jar（它们不在仓库里，需自己下载）：
   - MTR 3.6.3 的 **release** jar（不要 `-dev-shadow`）
   - Architectury API 9.2.14 的 fabric jar
2. 复制模板并填路径：
   ```sh
   cp local.properties.example local.properties
   ```
   ```properties
   yomimtr_jar=D:/mods/MTR-fabric-1.20.1-3.6.3.jar
   architectury_jar=D:/mods/architectury-9.2.14-fabric.jar
   ```
3. 构建：
   ```sh
   gradlew.bat build     # Windows
   ./gradlew build       # Linux / macOS
   ```

产物：`build/libs/trdm-DEV_0.1.jar`（约 27 MB，含内置字体与 JCodec）。

首次构建会下载 Gradle 9.5.0、JDK 17 toolchain 与依赖，通常 5–15 分钟。

## 安装

把 `trdm-DEV_0.1.jar` 放进 `mods/`。**客户端与服务端都要装**，服务端还需要 MTR 与 Architectury API。

## 使用

| 操作 | 效果 |
|---|---|
| 刷子右键 PIS 屏幕 | 打开屏幕设置（跑马灯、广告开关/时长/顺序、重新扫描资源包） |
| 刷子右键屏蔽门集控装置 | 选择本站站台；底部「屏蔽门隔离」让该站台只受本装置控制 |
| 刷子右键站名牌 | 选择本站站台 |
| 刷子右键站外站名牌 | 站名后缀（STATION/ZHAN）、无障碍电梯牌、出口 |
| 蹲下 + 刷子右键站台门 / 玻璃 | 站台门外观（吊版样式、玻璃装饰线样式） |
| `/trdm config`、快捷键、Mod Menu | 通用设置 |

PIS 广告由资源包驱动：`assets/trdm/pis_ads.json` + `assets/trdm/textures/block/pis/ads/` 下的
png / gif / mp4。仓库内可用 `gradlew makeSamplePack` 生成一份示例资源包。

## 目录

```
src/main/java/top/xiaotang27/trdm/
  Trdm.java             模组入口（common + client）
  block/                方块与方块实体
  render/               方块实体渲染器 + 动态贴图烘焙（AWT）
  client/               全部界面（960×540 设计坐标）
  control/              车站范围、站台门隔离、样式表
  net/                  集控 / 设置同步网络包
  mixin/                对 MTR TrainServer 的最小注入
src/main/resources/
  fabric.mod.json       模组元数据
  assets/trdm/          贴图、字体、界面素材、站名牌预设
  assets/mtr/           对 MTR 资源的选择性覆盖
```

## 许可

代码以 **MIT** 发布，见 [LICENSE](LICENSE)。
随附的第三方素材与库（MTR、思源黑体、JCodec、社区素材）各自的许可与署名见
[THIRD-PARTY-NOTICES.md](THIRD-PARTY-NOTICES.md)。
