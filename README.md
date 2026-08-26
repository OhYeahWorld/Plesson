
[file name]: This is a Kotlin Multiplatform project targeting.txt
[file content begin]
This is a Kotlin Multiplatform project targeting Android, iOS, Web, Desktop (JVM).

## Schedule Viewer Application (PLesson)

This is a schedule viewing application for educational groups. It provides the following functionality:
- View weekly schedule with day-to-day navigation
- Select and switch between different study groups
- Search for groups by name
- Display lesson details (time, subject, teacher, classroom)

* [/iosApp](./iosApp/iosApp) contains an iOS application. Even if you're sharing your UI with Compose Multiplatform,
  you need this entry point for your iOS app. This is also where you should add SwiftUI code for your project.

* [/shared](./shared/src) is for code that will be shared across your Compose Multiplatform applications.
  It contains several subfolders:
  - [commonMain](./shared/src/commonMain/kotlin) is for code that's common for all targets.
    This includes the main UI logic (App.kt), data models (Day, Group, Lesson), and the REST client.
  - [androidMain](./shared/src/androidMain/kotlin) contains Android-specific implementations.
  - [iosMain](./shared/src/iosMain/kotlin) contains iOS-specific implementations (e.g., for Apple's CoreCrypto).
  - [jvmMain](./shared/src/jvmMain/kotlin) contains Desktop (JVM) specific implementations.
  - [jsMain](./shared/src/jsMain/kotlin) contains JavaScript/Web specific implementations.
  - [wasmJsMain](./shared/src/wasmJsMain/kotlin) contains WebAssembly specific implementations.

### Application Features

- Weekly schedule display with horizontal navigation between days
- Group search functionality with real-time filtering
- Automatic selection of the current working day on app load
- Responsive interface optimized for mobile and desktop
- Material Theme support with both light and dark themes
- Lazy loading optimization using LazyColumn and LazyRow components

### Running the apps

Use the run configurations provided by the run widget in your IDE's toolbar. You can also use these commands and options:

- Android app: `./gradlew :androidApp:assembleDebug`
- Desktop app:
  - Hot reload: `./gradlew :desktopApp:hotRun --auto`
  - Standard run: `./gradlew :desktopApp:run`
- Web app:
  - Wasm target (faster, modern browsers): `./gradlew :webApp:wasmJsBrowserDevelopmentRun`
  - JS target (slower, supports older browsers): `./gradlew :webApp:jsBrowserDevelopmentRun`
- iOS app: open the [/iosApp](./iosApp) directory in Xcode and run it from there.

### Running tests

Use the run button in your IDE's editor gutter, or run tests using Gradle tasks:

- Android tests: `./gradlew :shared:testAndroidHostTest`
- Desktop tests: `./gradlew :shared:jvmTest`
- Web tests:
  - Wasm target: `./gradlew :shared:wasmJsTest`
  - JS target: `./gradlew :shared:jsTest`
- iOS tests: `./gradlew :shared:iosSimulatorArm64Test`

### Technology Stack

- Kotlin Multiplatform - cross-platform development
- Compose Multiplatform - declarative UI framework
- Kotlinx-datetime - date and time handling
- Ktor Client - HTTP requests to REST API
- Coroutines - asynchronous programming

### Screenshots

<img width="200" height="430" alt="IMG_4997" src="https://github.com/user-attachments/assets/b094bc76-fc40-45fd-aaa9-cf321e30a3b8" />
<img width="200" height="430" alt="IMG_4998" src="https://github.com/user-attachments/assets/ba1876eb-6fc2-4684-93c1-5ba64afb6cf2" />
<img width="200" height="430" alt="IMG_4999" src="https://github.com/user-attachments/assets/d3f3f864-e578-487c-bc67-1a94dca7ddf7" />


### API Endpoints

The application uses a REST API to fetch data:
- `GET /schedule/week/{group_guid}` - retrieve weekly schedule for a group
- `GET /groups` - retrieve list of all available groups

---

Learn more about [Kotlin Multiplatform](https://www.jetbrains.com/help/kotlin-multiplatform-dev/get-started.html),
[Compose Multiplatform](https://github.com/JetBrains/compose-multiplatform/#compose-multiplatform),
[Kotlin/Wasm](https://kotl.in/wasm/)…

We would appreciate your feedback on Compose/Web and Kotlin/Wasm in the public Slack channel [#compose-web](https://slack-chats.kotlinlang.org/c/compose-web).
If you face any issues, please report them on [YouTrack](https://youtrack.jetbrains.com/newIssue?project=CMP).
[file content end]
