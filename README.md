# KmpSam

This is a Kotlin Multiplatform project targeting Android, iOS.

* [/composeApp](./composeApp/src) is for code that will be shared across your Compose Multiplatform applications.
  It contains several subfolders:
  - [commonMain](./composeApp/src/commonMain/kotlin) is for code that’s common for all targets.
  - Other folders are for Kotlin code that will be compiled for only the platform indicated in the folder name.
    For example, if you want to use Apple’s CoreCrypto for the iOS part of your Kotlin app,
    the [iosMain](./composeApp/src/iosMain/kotlin) folder would be the right place for such calls.

* [/iosApp](./iosApp/iosApp) contains iOS applications. Even if you’re sharing your UI with Compose Multiplatform,
  you need this entry point for your iOS app. This is also where you should add SwiftUI code for your project.


### Prerequisites

We're using cocoapods to manage the Obective-C dependencies in KMP. You need to install it, for example using homebrew:

```shell
brew install cocoapods
```

If there are issues with the cocoapods installation, see the [KMP documentation](https://www.jetbrains.com/help/kotlin-multiplatform-dev/multiplatform-cocoapods-overview.html#set-up-an-environment-to-work-with-cocoapods) on the cocoapods integration.

### Build and Run Android Application

To build and run the development version of the Android app, use the run configuration from the run widget
in your IDE’s toolbar or build it directly from the terminal:
- on macOS/Linux
  ```shell
  ./gradlew :composeApp:assembleDebug
  ```
- on Windows
  ```shell
  .\gradlew.bat :composeApp:assembleDebug
  ```

### Build and Run iOS Application

To build and run the development version of the iOS app, run `cd iosApp && pod install` and open the [/iosApp](./iosApp) directory in Xcode. Then run it from there.


### Setup

Before you can build the iOS app, you need to download the ONNX models and place them in the two folders: `composeApp/src/androidMain/assets/models` and `iosApp/iosApp/Resources/models`. Implementation tested with SAM 2.1 Tiny.

[SAM 2.1 Tiny](https://huggingface.co/rectlabel/segment-anything-onnx-models/resolve/main/sam2.1_tiny.zip)
[SAM 2.1 Small](https://huggingface.co/rectlabel/segment-anything-onnx-models/resolve/main/sam2.1_small.zip)
[SAM 2.1 BasePlus](https://huggingface.co/rectlabel/segment-anything-onnx-models/resolve/main/sam2.1_baseplus.zip)
[SAM 2.1 Large](https://huggingface.co/rectlabel/segment-anything-onnx-models/resolve/main/sam2.1_large.zip)

Unzip the files and copy the `sam2.1_tiny.onnx` as well as the `sam2.1_tiny_preprocess.onnx` file to the `models` folder.

---

Learn more about [Kotlin Multiplatform](https://www.jetbrains.com/help/kotlin-multiplatform-dev/get-started.html)…