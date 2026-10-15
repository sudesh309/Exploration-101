# KidLearn

An offline Android learning app for children aged **5–15**. It ships with starter courses in
**Maths, Science, Robotics and AI**. Any other subject can be added by importing a course
pack, with no code changes.

* **Four learning modes:** pictures and text, audio recordings, videos, and quizzes. One lesson
  can mix them freely.
* **Content as data:** a course is a folder with a `course.json` and its media files, zipped
  into a pack. Import a pack to create a course; import a newer version to update it. See
  [docs/CONTENT_FORMAT.md](docs/CONTENT_FORMAT.md).
* **Import from anywhere:** a file picker (storage, SD card, USB drive, cloud drives), a web
  link, a USB import folder, "Open with" from other apps, or packs bundled in the APK.
* **Fully offline:** courses are stored on the phone. The internet is only needed when a parent
  or teacher downloads a pack from a link.
* **Learner profiles:** each child has their own profile with progress tracking, marks,
  grades and stars, broken down by subject, course and quiz.
* **Built for 3 GB phones:** see [Performance](#performance-on-low-end-phones).

## Project structure

```
app/                 Navigation, dependency wiring, Application (Coil tuning, bundled-pack sync)
core/domain          Pure Kotlin: models, repository interfaces, use cases (grading, marks)
core/content         Pure Kotlin: course-pack parser, validator, safe installer/updater
core/data            Android: Room database (profiles, progress), file-based course repository
core/ui              Android: theme, shared components, the BlockRenderer plug-in API
feature/profiles     Learner profiles (pick / create / edit / delete)
feature/catalog      Home (subjects, age-filtered courses) and course outline
feature/lesson       Lesson player + text & image renderers (visual mode)
feature/media        Audio & video renderers (Media3 ExoPlayer)
feature/quiz         Quiz renderer (all question types)
feature/results      Marks and grades report
feature/importer     Import/update/remove course packs
tools/packer         Command-line tool for content authors: validate and zip a course folder
sample-content/      Source folders of the bundled sample courses
```

Dependencies point inwards only: `feature → core:ui → core:domain`, and
`core:data → core:content → core:domain`. Feature modules never depend on each other or on
`core:data`; the `app` module is the only place that wires everything together. The domain and
content modules are plain Kotlin, so they build and test on any JVM.

### Extending the app

| You want to add... | Do this |
| --- | --- |
| A new subject or course | Nothing in code. Write a `course.json` and import the pack. |
| A new kind of lesson content (e.g. a drawing canvas or a simulation) | 1. Add a `ContentBlock` implementation in `core:domain`. 2. Add a `BlockParser` in `core:content` and register it in `BlockParserRegistry.default()`. 3. Add a `BlockRenderer` in a feature module and register it in `AppContainer.blockRenderers`. |
| A new quiz question type | Implement `Question` (domain) and `QuestionParser` (content), and add its UI in `feature/quiz`. |

## Performance on low-end phones

* **One media player at most.** `ActivePlayer` releases the previous player when another
  recording or video starts. Players are created only on tap, use smaller buffers than the
  defaults, and are released when their block scrolls off screen or the lesson closes.
* **Images** are decoded at display size (Coil), with RGB_565 where possible and a memory
  cache capped at 15 % of the app's heap.
* **Lazy everything:** lessons render in lazy lists, so off-screen blocks are disposed. Only
  four parsed courses are cached (LRU). Dependencies are created on first use. There is no
  DI framework or reflection-heavy JSON mapping.
* **Streaming I/O:** pack import streams zip entries to disk with a 64 KB buffer, so even large
  videos import in constant memory. Packs bundled in the APK are only unpacked after an app
  install or update.
* **Release builds** use R8 code and resource shrinking.
* `minSdk 24` (Android 7.0).

## Building

Requirements: JDK 17+ and the Android SDK (API 35).

```bash
./gradlew assembleDebug                         # app/build/outputs/apk/debug/app-debug.apk
./gradlew :core:domain:test :core:content:test  # pure-Kotlin unit tests
```

CI (`.github/workflows/android.yml`) runs the unit tests, validates every sample pack and
builds debug and release APKs, which you can download from the workflow run.

### Regenerating the sample courses

```bash
python3 tools/sample-content/generate.py sample-content    # needs Pillow + ffmpeg
./gradlew :tools:packer:installDist
for d in sample-content/*/; do
  tools/packer/build/install/packer/bin/packer "$d" "app/src/main/assets/bundled/$(basename "$d").zip"
done
```
