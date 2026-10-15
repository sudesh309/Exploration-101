#!/usr/bin/env python3
"""Generates the sample course folders in sample-content/ (manifests + media).

Requires Pillow and ffmpeg. Media is deliberately small and low-resolution, which is also the
recommended practice for real packs targeting low-end phones (see docs/CONTENT_FORMAT.md).

Usage: python3 tools/sample-content/generate.py [output-dir]
"""
import json
import math
import subprocess
import sys
from pathlib import Path

from PIL import Image, ImageDraw, ImageFont

OUT = Path(sys.argv[1] if len(sys.argv) > 1 else "sample-content")
FONT_PATH = "/usr/share/fonts/truetype/dejavu/DejaVuSans-Bold.ttf"
W, H = 640, 400


def font(size):
    try:
        return ImageFont.truetype(FONT_PATH, size)
    except OSError:
        return ImageFont.load_default()


def canvas(bg):
    img = Image.new("RGB", (W, H), bg)
    return img, ImageDraw.Draw(img)


def save(img, course, name):
    path = OUT / course / "images" / name
    path.parent.mkdir(parents=True, exist_ok=True)
    img.save(path, "WEBP", quality=78, method=6)
    return f"images/{name}"


def label(d, text, y, size=40, fill="#333333"):
    f = font(size)
    w = d.textlength(text, font=f)
    d.text(((W - w) / 2, y), text, font=f, fill=fill)


def apples(n, course, name):
    img, d = canvas("#FFF8E1")
    cols = 5
    for i in range(n):
        cx = 90 + (i % cols) * 115
        cy = 140 + (i // cols) * 130
        d.ellipse([cx - 45, cy - 45, cx + 45, cy + 45], fill="#E53935")
        d.rectangle([cx - 4, cy - 62, cx + 4, cy - 40], fill="#6D4C41")
        d.ellipse([cx + 4, cy - 66, cx + 34, cy - 50], fill="#43A047")
    return save(img, course, name)


def sum_picture(a, b, course, name):
    img, d = canvas("#E3F2FD")
    def stars(n, x0):
        for i in range(n):
            cx, cy = x0 + (i % 2) * 70, 120 + (i // 2) * 80
            pts = [(cx + 30 * math.cos(math.radians(-90 + k * 36)) * (1 if k % 2 == 0 else 0.45),
                    cy + 30 * math.sin(math.radians(-90 + k * 36)) * (1 if k % 2 == 0 else 0.45)) for k in range(10)]
            d.polygon(pts, fill="#FFB300")
    stars(a, 60)
    d.text((250, 130), "+", font=font(90), fill="#1E88E5")
    stars(b, 340)
    label(d, f"{a} + {b} = {a + b}", 320, 46, "#1565C0")
    return save(img, course, name)


def plant_stages(course, name):
    img, d = canvas("#E8F5E9")
    d.rectangle([0, 300, W, H], fill="#8D6E63")
    for i, h in enumerate([0, 40, 110, 190]):
        x = 90 + i * 150
        if h == 0:
            d.ellipse([x - 14, 290, x + 14, 310], fill="#5D4037")
        else:
            d.rectangle([x - 4, 300 - h, x + 4, 300], fill="#2E7D32")
            for k in range(1, h // 40 + 1):
                y = 300 - k * 38
                d.ellipse([x - 40, y - 12, x - 4, y + 8], fill="#43A047")
                d.ellipse([x + 4, y - 12, x + 40, y + 8], fill="#43A047")
            if i == 3:
                d.ellipse([x - 26, 300 - h - 40, x + 26, 300 - h + 10], fill="#FDD835")
    for i, t in enumerate(["Seed", "Sprout", "Plant", "Flower"]):
        f = font(26)
        d.text((90 + i * 150 - d.textlength(t, font=f) / 2, 350), t, font=f, fill="white")
    d.ellipse([20, 20, 110, 110], fill="#FFCA28")
    return save(img, course, name)


def plant_needs(course, name):
    img, d = canvas("#F1F8E9")
    items = [("☀", "Sunlight", "#FFB300"), ("💧", "Water", "#1E88E5"), ("🌬", "Air", "#90A4AE"), ("🟫", "Soil", "#6D4C41")]
    for i, (_, text, color) in enumerate(items):
        x = 40 + i * 150
        d.rounded_rectangle([x, 110, x + 130, 260], 24, fill=color)
        f = font(26)
        d.text((x + 65 - d.textlength(text, font=f) / 2, 170), text, font=f, fill="white")
    label(d, "What plants need", 30, 40, "#33691E")
    return save(img, course, name)


def robot(course, name):
    img, d = canvas("#ECEFF1")
    d.rectangle([250, 60, 390, 160], fill="#78909C", outline="#37474F", width=6)
    d.ellipse([275, 90, 305, 120], fill="#00E5FF")
    d.ellipse([335, 90, 365, 120], fill="#00E5FF")
    d.line([320, 60, 320, 30], fill="#37474F", width=6)
    d.ellipse([308, 14, 332, 38], fill="#F44336")
    d.rectangle([230, 170, 410, 320], fill="#90A4AE", outline="#37474F", width=6)
    d.rectangle([180, 180, 225, 290], fill="#78909C")
    d.rectangle([415, 180, 460, 290], fill="#78909C")
    d.ellipse([245, 310, 305, 370], fill="#37474F")
    d.ellipse([335, 310, 395, 370], fill="#37474F")
    d.text((40, 90), "SENSE", font=font(30), fill="#00838F")
    d.text((480, 230), "ACT", font=font(30), fill="#EF6C00")
    d.text((270, 225), "THINK", font=font(28), fill="#283593")
    return save(img, course, name)


def sense_think_act(course, name):
    img, d = canvas("#FFFDE7")
    boxes = [("Sense", "#26C6DA"), ("Think", "#5C6BC0"), ("Act", "#FF7043")]
    for i, (t, c) in enumerate(boxes):
        x = 40 + i * 210
        d.rounded_rectangle([x, 140, x + 170, 260], 28, fill=c)
        f = font(36)
        d.text((x + 85 - d.textlength(t, font=f) / 2, 180), t, font=f, fill="white")
        if i < 2:
            d.polygon([(x + 178, 190), (x + 202, 200), (x + 178, 210)], fill="#424242")
    label(d, "How every robot works", 50, 38)
    return save(img, course, name)


def neural_net(course, name):
    img, d = canvas("#EDE7F6")
    layers = [3, 4, 2]
    pos = []
    for li, n in enumerate(layers):
        x = 130 + li * 190
        col = [(x, 80 + (H - 160) * (k + 0.5) / n) for k in range(n)]
        pos.append(col)
    for a, b in zip(pos, pos[1:]):
        for p in a:
            for q in b:
                d.line([p, q], fill="#9575CD", width=3)
    colors = ["#42A5F5", "#7E57C2", "#66BB6A"]
    for li, col in enumerate(pos):
        for (x, y) in col:
            d.ellipse([x - 28, y - 28, x + 28, y + 28], fill=colors[li], outline="white", width=4)
    d.text((70, 350), "input", font=font(24), fill="#333")
    d.text((290, 350), "learning", font=font(24), fill="#333")
    d.text((470, 350), "answer", font=font(24), fill="#333")
    return save(img, course, name)


def cats_dogs(course, name):
    img, d = canvas("#FFF3E0")
    for i in range(3):
        x = 70 + i * 90
        d.ellipse([x - 35, 120, x + 35, 190], fill="#FFA726")
        d.polygon([(x - 30, 132), (x - 22, 100), (x - 8, 125)], fill="#FFA726")
        d.polygon([(x + 30, 132), (x + 22, 100), (x + 8, 125)], fill="#FFA726")
    for i in range(3):
        x = 380 + i * 90
        d.ellipse([x - 35, 120, x + 35, 190], fill="#8D6E63")
        d.ellipse([x - 45, 125, x - 25, 175], fill="#5D4037")
        d.ellipse([x + 25, 125, x + 45, 175], fill="#5D4037")
    d.text((110, 220), "cats", font=font(32), fill="#E65100")
    d.text((430, 220), "dogs", font=font(32), fill="#4E342E")
    label(d, "Examples with labels → the computer learns", 300, 24)
    return save(img, course, name)


def cover(course, title, emoji_shapes, bg):
    img, d = canvas(bg)
    emoji_shapes(d)
    label(d, title, 300, 44, "white")
    return save(img, course, "cover.webp")


def beeps(course, name, count):
    """Audio: `count` short beeps, as AAC in .m4a (plays everywhere, tiny)."""
    path = OUT / course / "audio" / name
    path.parent.mkdir(parents=True, exist_ok=True)
    expr = "+".join(f"between(t,{i * 0.7:.1f},{i * 0.7 + 0.25:.2f})" for i in range(count))
    subprocess.run([
        "ffmpeg", "-y", "-loglevel", "error", "-f", "lavfi", "-i",
        f"sine=frequency=660:duration={count * 0.7 + 0.5}",
        "-af", f"volume='if({expr},1,0)':eval=frame", "-ac", "1", "-c:a", "aac", "-b:a", "48k", str(path),
    ], check=True)
    return f"audio/{name}"


def melody(course, name):
    path = OUT / course / "audio" / name
    path.parent.mkdir(parents=True, exist_ok=True)
    notes = [523, 587, 659, 698, 784, 698, 659, 587, 523]
    inputs = []
    for f in notes:
        inputs += ["-f", "lavfi", "-i", f"sine=frequency={f}:duration=0.35"]
    subprocess.run(["ffmpeg", "-y", "-loglevel", "error", *inputs, "-filter_complex",
                    "".join(f"[{i}:a]" for i in range(len(notes))) + f"concat=n={len(notes)}:v=0:a=1[a]",
                    "-map", "[a]", "-ac", "1", "-c:a", "aac", "-b:a", "48k", str(path)], check=True)
    return f"audio/{name}"


def counting_video(course, name, upto=5):
    """Video: a count-along clip, 480x270 H.264 baseline (decodes on any phone)."""
    path = OUT / course / "video" / name
    path.parent.mkdir(parents=True, exist_ok=True)
    duration = upto * 1.2 + 1
    text = "if(lt(t,1),'Count with me!',floor((t-1)/1.2)+1)"
    draw = (f"drawtext=fontfile={FONT_PATH}:text='%{{eif\\:floor((t-1)/1.2)+1\\:d}}':fontsize=120:fontcolor=white:"
            f"x=(w-text_w)/2:y=(h-text_h)/2:enable='gte(t,1)',"
            f"drawtext=fontfile={FONT_PATH}:text='Count with me!':fontsize=36:fontcolor=white:x=(w-text_w)/2:y=(h-text_h)/2:enable='lt(t,1)'")
    subprocess.run([
        "ffmpeg", "-y", "-loglevel", "error",
        "-f", "lavfi", "-i", f"color=c=0x5B4CDB:s=480x270:d={duration}:r=24",
        "-f", "lavfi", "-i", f"sine=frequency=440:duration={duration}",
        "-vf", draw, "-af", "volume=0.15",
        "-c:v", "libx264", "-profile:v", "baseline", "-pix_fmt", "yuv420p", "-crf", "30",
        "-c:a", "aac", "-b:a", "48k", "-ac", "1", "-shortest", "-movflags", "+faststart", str(path),
    ], check=True)
    poster = Image.new("RGB", (480, 270), "#5B4CDB")
    pd = ImageDraw.Draw(poster)
    f = font(40)
    t = "Count with me!"
    pd.text(((480 - pd.textlength(t, font=f)) / 2, 110), t, font=f, fill="white")
    poster_path = save(poster, course, "count-video-poster.webp")
    return f"video/{name}", poster_path


def write(course_id, manifest):
    path = OUT / course_id / "course.json"
    path.parent.mkdir(parents=True, exist_ok=True)
    path.write_text(json.dumps(manifest, indent=2, ensure_ascii=False) + "\n")


def maths():
    c = "maths-counting-fun"
    video, poster = counting_video(c, "count-along.mp4")
    write(c, {
        "schemaVersion": 1, "id": c, "version": 1,
        "title": "Counting Fun",
        "description": "Count, listen and add with apples, stars and beeps.",
        "subject": {"id": "maths", "name": "Maths", "color": "#FF7043"},
        "ageRange": {"min": 5, "max": 8},
        "cover": cover(c, "Counting Fun", lambda d: [d.ellipse([100 + i * 90, 100, 170 + i * 90, 170], fill="#FFF") for i in range(5)], "#FF7043"),
        "units": [
            {"id": "counting", "title": "Counting", "lessons": [
                {"id": "count-apples", "title": "Count the apples", "summary": "Look and count", "points": 10, "blocks": [
                    {"type": "text", "style": "heading", "text": "Let's count!"},
                    {"type": "text", "text": "Point at each apple with your finger and say the numbers out loud: one, two, three..."},
                    {"type": "image", "src": apples(5, c, "five-apples.webp"), "caption": "How many apples?", "alt": "Five red apples"},
                    {"type": "text", "text": "There are 5 apples! When we count, the last number we say tells us how many there are."},
                    {"type": "text", "style": "fun_fact", "text": "People have been counting for over 40,000 years - long ago they used tally marks on bones!"},
                ]},
                {"id": "listen-count", "title": "Listen and count", "summary": "Count with your ears", "points": 10, "blocks": [
                    {"type": "text", "text": "Close your eyes and listen. Count every beep you hear."},
                    {"type": "audio", "src": beeps(c, "four-beeps.m4a", 4), "title": "Beep counting", "transcript": "Beep, beep, beep, beep. That was 4 beeps!"},
                    {"type": "text", "text": "Did you count 4 beeps? Great listening!"},
                ]},
                {"id": "count-video", "title": "Count-along video", "summary": "Watch and count", "points": 10, "blocks": [
                    {"type": "video", "src": video, "poster": poster, "title": "Count to 5 with me"},
                    {"type": "text", "text": "Can you count to 5 without the video? Try it now!"},
                ]},
                {"id": "counting-quiz", "title": "Counting quiz", "summary": "Show what you know", "points": 5, "blocks": [
                    {"type": "quiz", "id": "counting-quiz", "title": "Counting quiz", "passPercent": 60, "questions": [
                        {"id": "apples", "type": "single_choice", "prompt": "How many apples are there?",
                         "image": apples(3, c, "three-apples.webp"), "options": ["2", "3", "4"], "answer": 1},
                        {"id": "after-four", "type": "single_choice", "prompt": "Which number comes after 4?", "options": ["3", "5", "6"], "answer": 1},
                        {"id": "beeps", "type": "number", "prompt": "How many beeps did you hear in the listening lesson?", "answer": 4},
                        {"id": "ten-big", "type": "true_false", "prompt": "10 is bigger than 7.", "answer": True,
                         "explanation": "When we count, 10 comes after 7, so it is bigger."},
                    ]},
                ]},
            ]},
            {"id": "adding", "title": "Adding", "lessons": [
                {"id": "putting-together", "title": "Putting things together", "points": 10, "blocks": [
                    {"type": "text", "style": "heading", "text": "Adding means putting together"},
                    {"type": "image", "src": sum_picture(2, 3, c, "two-plus-three.webp"), "caption": "2 stars and 3 stars make 5 stars"},
                    {"type": "text", "text": "We write it like this: 2 + 3 = 5. The + sign means 'add' and = means 'is the same as'."},
                ]},
                {"id": "adding-quiz", "title": "Adding quiz", "points": 5, "blocks": [
                    {"type": "quiz", "id": "adding-quiz", "questions": [
                        {"id": "q1", "type": "number", "prompt": "1 + 1 = ?", "answer": 2},
                        {"id": "q2", "type": "number", "prompt": "2 + 3 = ?", "answer": 5},
                        {"id": "q3", "type": "multiple_choice", "prompt": "Which ones make 4? (pick all)",
                         "options": ["2 + 2", "3 + 1", "1 + 2"], "answer": [0, 1], "points": 2},
                    ]},
                ]},
            ]},
        ],
    })


def science():
    c = "science-how-plants-grow"
    write(c, {
        "schemaVersion": 1, "id": c, "version": 1,
        "title": "How Plants Grow",
        "description": "From a tiny seed to a big flower: what plants need to live.",
        "subject": {"id": "science", "name": "Science", "color": "#43A047"},
        "ageRange": {"min": 6, "max": 10},
        "cover": cover(c, "How Plants Grow", lambda d: d.ellipse([270, 80, 370, 180], fill="#FDD835"), "#43A047"),
        "units": [{"id": "plants", "title": "Plants", "lessons": [
            {"id": "seed-to-flower", "title": "From seed to flower", "points": 10, "blocks": [
                {"type": "text", "style": "heading", "text": "A plant's life"},
                {"type": "image", "src": plant_stages(c, "stages.webp"), "caption": "Seed → sprout → plant → flower"},
                {"type": "text", "text": "A seed has a tiny baby plant inside. With water and warmth it sprouts: a root grows down and a shoot grows up towards the light."},
                {"type": "audio", "src": melody(c, "growing-song.m4a"), "title": "Growing song", "transcript": "Seed in the soil, sleeping tight. Add some water, add some light. Up comes a sprout, green and small, soon it's a flower, standing tall!"},
                {"type": "text", "style": "fun_fact", "text": "Some seeds can wait more than 1,000 years before they sprout!"},
            ]},
            {"id": "plant-needs", "title": "What plants need", "points": 10, "blocks": [
                {"type": "image", "src": plant_needs(c, "needs.webp"), "caption": "Sunlight, water, air and soil"},
                {"type": "text", "text": "Plants make their own food from sunlight, water and air. This is called photosynthesis. Their roots also take in minerals from the soil."},
            ]},
            {"id": "plants-quiz", "title": "Plant quiz", "points": 5, "blocks": [
                {"type": "quiz", "id": "plants-quiz", "questions": [
                    {"id": "first", "type": "single_choice", "prompt": "What does a plant start as?", "options": ["A flower", "A seed", "A leaf"], "answer": 1},
                    {"id": "needs", "type": "multiple_choice", "prompt": "What do plants need? (pick all)", "options": ["Sunlight", "Water", "Chocolate", "Air"], "answer": [0, 1, 3], "points": 2},
                    {"id": "roots", "type": "true_false", "prompt": "Roots grow up towards the sky.", "answer": False, "explanation": "Roots grow down into the soil; shoots grow up."},
                    {"id": "word", "type": "text", "prompt": "What is it called when plants make food from sunlight?", "answers": ["photosynthesis"], "points": 2},
                ]},
            ]},
        ]}],
    })


def robotics():
    c = "robotics-meet-the-robots"
    write(c, {
        "schemaVersion": 1, "id": c, "version": 1,
        "title": "Meet the Robots",
        "description": "What makes a machine a robot? Sensors, a brain and motors!",
        "subject": {"id": "robotics", "name": "Robotics", "color": "#546E7A"},
        "ageRange": {"min": 8, "max": 12},
        "cover": cover(c, "Meet the Robots", lambda d: d.rectangle([270, 70, 370, 170], fill="#CFD8DC"), "#546E7A"),
        "units": [{"id": "basics", "title": "Robot basics", "lessons": [
            {"id": "what-is-a-robot", "title": "What is a robot?", "points": 10, "blocks": [
                {"type": "text", "style": "heading", "text": "Robots sense, think and act"},
                {"type": "image", "src": robot(c, "robot.webp"), "caption": "A robot has sensors, a controller and actuators"},
                {"type": "text", "text": "A robot is a machine that can SENSE the world around it, THINK about what to do, and ACT by moving or doing something."},
                {"type": "image", "src": sense_think_act(c, "loop.webp"), "caption": "The sense → think → act loop repeats many times every second"},
            ]},
            {"id": "robot-parts", "title": "Robot parts", "points": 10, "blocks": [
                {"type": "text", "text": "Sensors are the robot's eyes and ears: cameras, distance sensors, touch switches and microphones."},
                {"type": "text", "text": "The controller is the robot's brain: a small computer running a program."},
                {"type": "text", "text": "Actuators are the robot's muscles: motors that turn wheels, move arms or open grippers."},
                {"type": "text", "style": "fun_fact", "text": "The word 'robot' comes from the Czech word 'robota', meaning hard work. It was first used in a play in 1920."},
            ]},
            {"id": "robot-quiz", "title": "Robot quiz", "points": 5, "blocks": [
                {"type": "quiz", "id": "robot-quiz", "questions": [
                    {"id": "loop", "type": "single_choice", "prompt": "What is the robot loop?", "options": ["Sense → Think → Act", "Eat → Sleep → Play", "Act → Stop → Sense"], "answer": 0},
                    {"id": "sensors", "type": "multiple_choice", "prompt": "Which of these are sensors? (pick all)", "options": ["Camera", "Motor", "Touch switch", "Wheel"], "answer": [0, 2], "points": 2},
                    {"id": "brain", "type": "single_choice", "prompt": "Which part is the robot's brain?", "options": ["Actuator", "Controller", "Battery"], "answer": 1},
                    {"id": "year", "type": "number", "prompt": "In which year was the word 'robot' first used?", "answer": 1920},
                ]},
            ]},
        ]}],
    })


def ai():
    c = "ai-how-computers-learn"
    write(c, {
        "schemaVersion": 1, "id": c, "version": 1,
        "title": "How Computers Learn",
        "description": "A first look at Artificial Intelligence and machine learning.",
        "subject": {"id": "ai", "name": "Artificial Intelligence", "color": "#7E57C2"},
        "ageRange": {"min": 10, "max": 15},
        "cover": cover(c, "How Computers Learn", lambda d: [d.ellipse([200 + i * 80, 100, 250 + i * 80, 150], fill="#D1C4E9") for i in range(4)], "#7E57C2"),
        "units": [{"id": "ml", "title": "Machine learning", "lessons": [
            {"id": "what-is-ai", "title": "What is AI?", "points": 10, "blocks": [
                {"type": "text", "style": "heading", "text": "Artificial Intelligence"},
                {"type": "text", "text": "Artificial Intelligence (AI) is when computers do things that normally need human intelligence: recognising pictures, understanding speech, translating languages or playing games."},
                {"type": "text", "text": "Instead of writing every rule by hand, we often let the computer learn the rules from examples. This is called machine learning."},
            ]},
            {"id": "learning-from-examples", "title": "Learning from examples", "points": 10, "blocks": [
                {"type": "image", "src": cats_dogs(c, "examples.webp"), "caption": "Training data: pictures with labels"},
                {"type": "text", "text": "To teach a computer to tell cats from dogs we show it thousands of labelled pictures. It slowly adjusts itself until its guesses match the labels. Then it can guess labels for pictures it has never seen."},
                {"type": "image", "src": neural_net(c, "network.webp"), "caption": "A neural network: layers of connected 'neurons'"},
                {"type": "text", "style": "fun_fact", "text": "AI can be wrong! If the examples are unfair or too few, the AI learns the wrong lessons. That's why people must check and test AI carefully."},
            ]},
            {"id": "ai-quiz", "title": "AI quiz", "points": 5, "blocks": [
                {"type": "quiz", "id": "ai-quiz", "passPercent": 70, "questions": [
                    {"id": "ml", "type": "text", "prompt": "Learning rules from examples is called machine ____", "answers": ["learning"]},
                    {"id": "data", "type": "single_choice", "prompt": "What do we call the labelled examples a model learns from?", "options": ["Training data", "Hardware", "A password"], "answer": 0},
                    {"id": "wrong", "type": "true_false", "prompt": "AI is always right.", "answer": False, "explanation": "AI learns from data and can make mistakes, especially with poor or biased data."},
                    {"id": "uses", "type": "multiple_choice", "prompt": "Which of these can AI do? (pick all)", "options": ["Recognise faces in photos", "Translate languages", "Make you taller"], "answer": [0, 1], "points": 2},
                ]},
            ]},
        ]}],
    })


if __name__ == "__main__":
    maths()
    science()
    robotics()
    ai()
    print(f"Sample courses written to {OUT}/")
