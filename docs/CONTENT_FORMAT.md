# Course pack format

A **course pack** is how content gets into KidLearn. A pack is either

* a `.zip` containing `course.json` plus the media it references (pictures, audio, video), or
* a bare `course.json` (for text/quiz-only courses, or media referenced by `https://` URL).

`course.json` may sit at the root of the zip or inside a single top-level folder. A **bundle**
zip (for example a whole GitHub repository) may hold many course folders and nested `.zip`
packs; all of them are installed.

Importing a pack **creates the course**. Importing a pack with the same `id` and a higher
`version` **updates** it in place. Learner progress is kept because it is stored by
course id + lesson id. Progress for lessons you delete stops counting towards marks, and new
lessons simply show up as not done yet.

A **subject** is just a field in the pack. There is no list of subjects to maintain: a pack
declaring `"subject": {"id": "music", ...}` adds a Music subject to the app.

## Ways to import

| From | How |
| --- | --- |
| GitHub / Google Drive | **⚙️ → Download now** (see [SHARING_AND_SYNC.md](SHARING_AND_SYNC.md)) |
| Any file on the phone, SD card, USB-OTG drive, Google Drive, etc. | **⚙️ → Add from file / link → Choose a pack file** |
| A web link | **⚙️ → Add from file / link → paste link** (needs internet once; then works offline) |
| A computer over USB | Copy packs into `Android/data/com.kidlearn.app/files/import/`, then **⚙️ → Add from file / link → Scan import folder** |
| Chat / email / file manager | Open the `.zip` with **KidLearn** |
| Bundled with the app | Put packs in `app/src/main/assets/bundled/` (installed or updated automatically when the app is installed or updated) |

## `course.json`

```jsonc
{
  "schemaVersion": 1,                 // format version; the app refuses newer formats it can't read
  "id": "maths-counting-fun",         // letters, digits, - and _ ; must stay the same across updates
  "version": 2,                       // bump to publish an update
  "title": "Counting Fun",
  "description": "Count, listen and add.",
  "subject": { "id": "maths", "name": "Maths", "color": "#FF7043", "icon": "images/maths.webp" },
  "ageRange": { "min": 5, "max": 8 }, // the course shows for learners in this age range (5-15 if omitted)
  "cover": "images/cover.webp",
  "units": [
    {
      "id": "counting",
      "title": "Counting",
      "lessons": [
        {
          "id": "count-apples",         // unique within the course; keep stable across updates
          "title": "Count the apples",
          "summary": "Look and count",
          "points": 10,                 // marks for completing the lesson (default 10)
          "blocks": [ /* content blocks, shown top to bottom */ ]
        }
      ]
    }
  ]
}
```

All media paths are relative to `course.json`. A path can also be an `https://` URL, but that
media only plays when the phone is online.

## Content blocks: the learning modes

A lesson is a list of blocks, so one lesson can mix modes (for example a picture, then a
recording, then a quiz).

| type | fields | mode |
| --- | --- | --- |
| `text` | `text`, `style`: `body` (default) / `heading` / `fun_fact` | visual |
| `image` | `src`, `caption`, `alt` | visual |
| `audio` | `src`, `title`, `transcript` (shown on request) | audio |
| `video` | `src`, `title`, `poster` (image shown before playing) | video |
| `quiz` | `id`, `title`, `passPercent` (default 60), `questions` | quiz |

Unknown block types are skipped with a warning and shown as "needs a newer version of the
app". Older apps therefore keep working with packs written for newer ones.

## Quiz questions

Every question has `id`, `type`, `prompt`, and optionally `image`, `points` (default 1) and
`explanation` (shown after answering).

| type | answer fields |
| --- | --- |
| `single_choice` | `options: [..]`, `answer: <index>` (0-based) |
| `multiple_choice` | `options: [..]`, `answer: [<index>, ..]`: every correct option, and only those, must be picked |
| `true_false` | `answer: true \| false` |
| `number` | `answer: 4`, optional `tolerance: 0.5` |
| `text` | `answer: "Mars"` or `answers: ["photosynthesis", "photo synthesis"]` (case-insensitive) |

## Marks

* Completing a lesson earns its `points`.
* Each quiz contributes its **best** attempt (every attempt is stored).
* Course marks = lesson points + best quiz scores, out of the maximum possible. Grades:
  A+ ≥ 90 %, A ≥ 80 %, B ≥ 70 %, C ≥ 60 %, D ≥ 40 %.

## Media guidelines for low-end phones

* **Images:** WebP or JPEG, about 1280 px on the longest side at most. The app downsamples on
  load, but smaller files import and display faster.
* **Audio:** AAC (`.m4a`) or MP3, mono, 48–96 kbps.
* **Video:** H.264 MP4, 480p or 720p, ≤ 1.5 Mbps, with `+faststart`. For example:
  `ffmpeg -i in.mov -vf scale=-2:480 -c:v libx264 -profile:v main -crf 28 -c:a aac -b:a 64k -movflags +faststart out.mp4`

## Building a pack

```bash
./gradlew :tools:packer:installDist
tools/packer/build/install/packer/bin/packer path/to/my-course my-course.zip
```

The packer uses the app's own parser, so a pack it accepts imports into the app. It checks the
manifest, makes sure every referenced media file exists, and stores already-compressed media
uncompressed in the zip so phones unpack it quickly.

`sample-content/` contains four complete example courses (generated by
`tools/sample-content/generate.py`).
