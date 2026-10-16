# Kids' questions, sharing and online course sources

## Asking questions

Every element of a lesson (paragraph, picture, recording, video, quiz) has a **🙋 Ask a
question** button. The question is saved on the phone together with *what it was about*:
learner, course, lesson, element number and type, and a short preview of the element.

All questions live in one JSON file, `files/questions/questions.json` in the app's private
storage. Learners see theirs under **🙋** on the home screen.

### Question file format

```json
{
  "format": "kidlearn-questions",
  "version": 1,
  "deviceId": "3f1c…",
  "deviceName": "Class 3 tablet",
  "exportedAt": "2026-10-16T09:30:00Z",
  "questions": [
    {
      "id": "8b0e…", "learner": "Ada", "learnerId": 1, "age": 8,
      "courseId": "maths-counting-fun", "course": "Counting Fun",
      "lessonId": "count-apples", "lesson": "Count the apples",
      "elementIndex": 2, "elementType": "image", "element": "How many apples?",
      "question": "Why are some apples green?",
      "askedAt": "2026-10-16T09:12:44Z", "askedAtMillis": 1792141964000
    }
  ]
}
```

### Sharing the questions

On the questions screen:

* **📤 Share questions file** exports the log and opens Android's share sheet. Send it by
  email, WhatsApp or Bluetooth, or save it to **Google Drive** with the Drive app.
* **☁️ Upload to GitHub** commits the log to the configured repository as
  `<questions folder>/<device id>.json`. Each device writes its own file, so several tablets
  never overwrite each other. Uploading again replaces that device's file with the full,
  current log.

## Downloading courses (grown-ups)

Open **⚙️** on the home screen and answer the grown-up check to reach the settings. Set up
one or both sources, then tap **⬇️ Download now**. With an internet connection the app checks
**both** sources and installs every new or updated course it finds. If one source fails (a
wrong link, for example), the other still downloads.

A source that hasn't changed since the last download is skipped (GitHub compares the latest
commit; Drive compares file modification times). **Download everything again** forces a full
download. Learners' progress and marks are always kept.

### GitHub

| Setting | Meaning |
| --- | --- |
| Owner / Repository / Branch | e.g. `my-school` / `kidlearn-courses` / `main` |
| Courses folder | Optional, e.g. `courses`. Only this folder is searched. |
| Questions upload folder | Where question logs are uploaded (default `questions`) |
| Access token | Optional for public repositories. Required for private ones and for uploading. |

The repository can contain course folders (each with a `course.json` and its media) and/or
`.zip` course packs, at any depth:

```
kidlearn-courses/
  courses/
    maths-counting-fun/course.json, images/…, audio/…
    science-how-plants-grow/course.json, …
    extra/robotics.zip
  questions/            ← uploaded question logs appear here
```

To update a course, edit it in the repository and raise its `version`. Devices pick up the
change on their next download.

**Token:** create a *fine-grained personal access token* on GitHub (Settings → Developer
settings → Fine-grained tokens) limited to that one repository, with **Contents: Read and
write** permission (or *Read-only* if the device should only download). The token is stored
in the app's private storage and excluded from backups.

### Google Drive

* **A single shared file:** share a `.zip` (one course pack, or a bundle zip with many courses)
  as *Anyone with the link* and paste its link. No API key is needed.
* **A shared folder:** share the folder as *Anyone with the link*, paste the folder link, and
  add a Google API key (Google Cloud Console → enable the *Google Drive API* → Credentials →
  API key; restrict it to the Drive API). Every `.zip` and `.json` in the folder is downloaded.

Uploading questions straight to Google Drive would need each device to sign in to a Google
account (OAuth). Use **Share → Drive** instead, or upload to GitHub.
