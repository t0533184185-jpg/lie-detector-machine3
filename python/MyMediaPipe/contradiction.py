import socket
import cv2
import mediapipe as mp
import ssl
from transformers import pipeline

# עקיפת SSL והכנת מודל BERT
ssl._create_default_https_context = ssl._create_unverified_context
print("Loading BERT model...")
classifier = pipeline("zero-shot-classification", model="facebook/bart-large-mnli")

# הגדרת Mediapipe
mp_pose = mp.solutions.pose
pose = mp_pose.Pose(static_image_mode=True)

server = socket.socket(socket.AF_INET, socket.SOCK_STREAM)
server.bind(('localhost', 5050))
server.listen(1)
print("Python Server is ready!")

while True:
    conn, addr = server.accept()
    data = conn.recv(2048).decode('utf-8').strip()

    if data.startswith("IMG:"):
        # עיבוד תמונה
        img_path = data.replace("IMG:", "")
        conn.sendall("OK\n".encode('utf-8'))

    elif data.startswith("NLP:"):
        # עיבוד סתירות לוגיות
        # הודעה צפויה: "NLP:משפט1|משפט2"
        content = data.replace("NLP:", "")
        sentences = content.split("|")

        if len(sentences) == 2:
            p, h = sentences[0], sentences[1]
            labels = ["contradiction", "entailment", "neutral"]
            result = classifier(p, labels)
            score = result['scores'][result['labels'].index('contradiction')]

            # שליחת התוצאה לג'אווה
            response = f"SCORE:{score:.4f}\n"
            conn.sendall(response.encode('utf-8'))
        else:
            conn.sendall("ERROR:Invalid format\n".encode('utf-8'))

    conn.close()