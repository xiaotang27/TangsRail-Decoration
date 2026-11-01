# TangsRail Decoration

为 [Minecraft Transit Railway 3.x](https://github.com/jonafanho/Minecraft-Transit-Railway) 拓展了铁路设施。

> 本模组的代码与部分美术素材使用了生成式人工智能（AIGC）工具参与制作。


## 依赖

| 依赖 | 版本             | 说明                                                                                         |
|---|----------------|--------------------------------------------------------------------------------------------|
| Minecraft | 1.20.1         |                                                                                            |
| Fabric Loader | 0.19.3+        |                                                                                            |
| Fabric API | 0.92.11+1.20.1 |                                                                                            |
| Minecraft Transit Railway | 3.2.x          | mod id `mtr`，必须                                                                            |
| Mod Menu | 7.2.2+         | 可选                                                                                         |

## 构建

前置：**JDK 21**（fabric-loom 1.17.19 要求构建 JVM ≥ 21，但编译目标是 Java 17）。

构建 JVM 三种指定方式，任选其一：
1. 设置 `JAVA_HOME` 指向 JDK 21；
2. 在用户级 `~/.gradle/gradle.properties` 写 `org.gradle.java.home=<JDK 21 路径>`；
3. 在项目里临时加同名键（注意别提交）。

1. 准备两个本地 jar（它们不在仓库里，需自己下载）：
   - MTR 的 **release** jar（不要 `-dev-shadow`）
   - Architectury API 的 fabric jar
2. 复制模板并填路径：
   ```sh
   cp local.properties.example local.properties
   ```
   ```properties
   mtr_jar=D:/mods/MTR.jar
   architectury_jar=D:/mods/architectury.jar
   ```
3. 构建：
   ```sh
   gradlew.bat build     # Windows
   ./gradlew build       # Linux / macOS
   ```

产物：`build/libs/trdm-DEV_0.1.jar`（约 27 MB，含内置字体与 JCodec）。

首次构建会下载 Gradle 9.5.0、JDK 17 toolchain 与依赖，通常 5–15 分钟。

## 许可

代码以 **MIT** 发布，见 [LICENSE](LICENSE)。
随附的第三方素材与库（MTR、思源黑体、JCodec、社区素材）各自的许可与署名见
[THIRD-PARTY-NOTICES.md](THIRD-PARTY-NOTICES.md)。
