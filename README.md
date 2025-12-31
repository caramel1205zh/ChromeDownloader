# ChromeDownloader Tool
💕💕 Download Google Chrome offline installer for x64 Windows or macOS

## System Requirements
- **JDK**: Bellsoft Liberica Full JDK (includes JavaFX)
- **OS**: Windows or macOS
- **Architecture**: x64 (Windows) / Universal (macOS)

## Install Dependencies
1. Download and install Bellsoft Liberica Full JDK:
   [https://bell-sw.com/pages/downloads](https://bell-sw.com/pages/downloads)

## Compilation & Packaging

### Step 1: Compile Source Code
```bash
javac --add-modules javafx.controls,javafx.fxml *.java
```

### Step 2: Create Executable JAR
```bash
jar cfe app/ChromeDownloader.jar Main *.class i18n images/*.png
```

### Step 3: Create Native Application

**Windows Version:**
```bash
jpackage --type app-image --name "ChromeDownloader" --input app --main-jar ChromeDownloader.jar --main-class Main --icon images/*.ico
```

**macOS Version:**
```bash
jpackage --type dmg --name "ChromeDownloader" --input app --main-jar ChromeDownloader.jar --main-class Main --icon images/*.icns
```

## Usage Instructions
1. Launch the ChromeDownloader application
2. Select your preferred Chrome version from the list
3. Click the download button to get the offline installer 💕💕

## Portable Chrome
If you need a portable version of Chrome browser, try:
- **Chrome++**: [https://github.com/Bush2021/chrome_plus](https://github.com/Bush2021/chrome_plus)

