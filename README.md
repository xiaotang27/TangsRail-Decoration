# TangsRail Decoration

Extend the railway facilities for [Minecraft Transit Railway 3.x](https://github.com/jonafanho/Minecraft-Transit-Railway).

> Contain AI-generated script and art contents.


## Build

**JDK 21** required.

1. Prepare the following jars:
   - MTR **release** jar (without `-dev-shadow`)
   - Architectury API fabric jar (for [YMTR](https://github.com/yomi-china/Yomis-Minecraft-Transit-Railway) only)
2. Run the following commands:
   ```sh
   cp local.properties.example local.properties
   ```
   ```properties
   mtr_jar=D:/mods/MTR.jar
   
   # for YMTR only
   architectury_jar=D:/mods/architectury.jar 
   ```
3. Build:
   ```sh
   gradlew.bat build     # Windows
   ./gradlew build       # Linux / macOS
   ```
## Contributing
### Adding features
1. Fork this project
2. On your fork, create a new branch based on the development version branch
3. Commit your changes to the new branch
4. Make a Pull Request to merge your branch into the development version of this repository

## License

The License of this project is **MIT** . See [LICENSE](LICENSE).
Third-party dependencies and their licenses are disclosed in
[THIRD-PARTY-NOTICES.md](THIRD-PARTY-NOTICES.md) (in Chinese only).
