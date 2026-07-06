# Spring Mappings Search

A lightweight, high-performance IntelliJ IDEA plugin that indexes Spring Controller endpoints and enables fast URL searching and code navigation directly inside the **Search Everywhere** dialog.

## Features

- **Dedicated Search Tab**: Adds a dedicated **"Spring Mappings URLs"** tab to the Search Everywhere dialog (double shift) to keep URL searches clean and isolated from classes, files, or actions.
- **Fast In-Memory Index**: Scans project controllers on startup in the background (when the IDE is in smart mode) and caches URL routes in memory. No scanning is performed during search queries to ensure instant results.
- **Incremental Updates**: Listens to real-time PSI and VFS file changes. Adds, edits, or deletes endpoints on the fly with debounced updates (1s delay) to preserve IDE performance.
- **UAST-based Parsing**: Built on JetBrains Unified AST (UAST), enabling native support for both **Java** and **Kotlin** Spring controllers (`@Controller`, `@RestController`, `@RequestMapping`, `@GetMapping`, `@PostMapping`, `@PutMapping`, `@DeleteMapping`, `@PatchMapping`).
- **Path Combination & Normalization**: Automatically handles class-level mapping prefixes (supporting both `value` and `path` properties) and resolves nested or multiple endpoint mappings correctly.
- **Weighted Sorting**: Automatically ranks search matches based on query type:
  - Exact Match > Prefix Match > Contains Match > Fuzzy Match.
  - Penalizes longer URLs slightly so shorter/primary endpoints rank higher.
- **HTTP Method Filters**: Supports filtering by HTTP method (e.g. typing `GET /users` or `POST user`).
- **Direct Code Navigation**: Pressing Enter or double-clicking on a URL search result navigates directly to the target `PsiMethod` handler in the editor.
- **Rich Visual Rendering**: Features color-coded HTTP methods (Green `GET`, Blue `POST`, Orange `PUT`, Red `DELETE`, Purple `PATCH`) aligned vertically for clear reading, followed by bold paths and greyed class/handler information.
- **K2 Compiler Mode Support**: Fully compatible with the Kotlin K2 compiler mode in modern JetBrains IDEs.

## How to Build

The plugin requires JVM 17+ to run Gradle compilation.

1. Set your `JAVA_HOME` to JDK 17 or higher:
   ```powershell
   $env:JAVA_HOME="path/to/your/jdk17"
   ```
2. Build the plugin package:
   ```powershell
   .\gradlew.bat buildPlugin
   ```
   The built zip archive will be generated at `build/distributions/spring-mappings-search-0.0.1.zip`.

## Installation

1. Open IntelliJ IDEA.
2. Navigate to **Settings/Preferences** > **Plugins**.
3. Click the gear icon (⚙️) in the top-right corner of the Plugins page and select **Install Plugin from Disk...**.
4. Select the built plugin file: `build/distributions/spring-mappings-search-0.0.1.zip`.
5. Restart the IDE to load the plugin.
