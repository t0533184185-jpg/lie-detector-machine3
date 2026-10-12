import socket
import ssl
import re
import threading
import cv2
import mediapipe as mp
from transformers import pipeline
from deep_translator import GoogleTranslator
import json

# --- הגדרות אבטחה ותרגום ---
ssl._create_default_https_context = ssl._create_unverified_context

# =====================================================================
# טעינת מודל ה-NLP (חד פעמי, גלובלי)
# =====================================================================
print("Loading NLP Model (BART-MNLI)...")
model_path = r"C:\models\bart-large-mnli"
classifier = pipeline(task="text-classification", model=model_path, tokenizer=model_path)
print(type(classifier))
print(classifier.model.config.id2label)
# =====================================================================
#  טעינת מודלי שפת הגוף של MediaPipe
# =====================================================================
print("Loading Body Language Models (MediaPipe)...")
mp_pose = mp.solutions.pose
mp_face_mesh = mp.solutions.face_mesh
pose = mp_pose.Pose(static_image_mode=True)
face_mesh = mp_face_mesh.FaceMesh(static_image_mode=True, max_num_faces=1)

# מנעול תהליכונים כדי למנוע מפריימים להתנגש על מודל ה-MediaPipe
mediapipe_lock = threading.Lock()
nlp_lock = threading.Lock()
# =====================================================================
# פונקציית הטיפול בבקשות (רצה ב-Thread נפרד לכל פנייה)
# =====================================================================
def handle_client(conn, addr):

    try:
        data = conn.recv(65535).decode('utf-8').strip()
        print("RAW DATA:")
        print(repr(data))
        if not data:
            return


        #  : מציאת סתירות עקיפות
        clean_data = data.strip()

        if "NLP:" == clean_data[:4]:
            content_hebrew = data.replace("NLP:", "").strip()
            print(repr(clean_data[:20]))
            print(f"[NLP] Received raw payload: {content_hebrew}")


            #  מפרידים בין כל היסטוריית העבר לבין המשפט החדש (באמצעות ה- |)
            raw = data.replace("NLP:", "", 1).strip()
            payload = json.loads(raw)


            history = payload.get("history", [])
            new_fact = payload.get("newFact", {})
            sentence_facts = payload.get("sentenceFacts", [])
            # תרגום לאנגלית של שני החלקים בנפרד
            try:
                new_fact_he = new_fact.get("naturalSentenceHe", "")
                new_fact_en = new_fact.get("naturalSentenceEn", "")

            except Exception as e:

                print(f"[NLP] JSON parse error: {e}")

                conn.sendall("NO_CONTRADICTION|SCORE:0.0\n".encode('utf-8'))

                return

            #  פירוק היסטוריית העבר למשפטים בודדים (למשל: רשימה של משפט 1, משפט 2, משפט 3)
            past_facts = []

            for fact in history:
                past_fact_he = fact.get("naturalSentenceHe", "")
                past_fact_en = fact.get("naturalSentenceEn", "")

                past_facts.append({
                    "he": past_fact_he,
                    "en": past_fact_en
                })

            found_contradiction = False
            highest_score = 0.0
            reason = ""
            best_old_fact = ""
            best_new_fact = ""

            #  משווים את משפט  (החדש) מול כל אחד ואחד ממשפטי העבר
            if past_facts and new_fact_en:
                for past_fact in past_facts:
                    with nlp_lock:
                        text = past_fact["en"] + " </s></s> " + new_fact_en
                        
                        result = classifier(text)[0]

                    print(result)
                    label = result["label"].upper()
                    score = result["score"]

                    is_contradiction = (label == "CONTRADICTION")

                    if is_contradiction and score > highest_score:
                        highest_score = score
                        best_old_fact = past_fact["he"] if past_fact["he"] else past_fact["en"]
                        best_new_fact = new_fact_he if new_fact_he else new_fact_en
                        reason = "סתירה לוגית מול ידע קודם"

                        if highest_score > 0.4:
                            found_contradiction = True









            for fact in sentence_facts:

                # מדלגים על העובדה החדשה עצמה
                if fact.get("subjectName") == new_fact.get("subjectName") and \
                        fact.get("action") == new_fact.get("action") and \
                        fact.get("objectName") == new_fact.get("objectName"):
                    continue

                text_he = fact.get("naturalSentenceHe", "")

                text_en = fact.get("naturalSentenceEn", "")


                with nlp_lock:
                    text = text_en + " </s></s> " + new_fact_en
                    
                    result = classifier(text)[0]


                    print(result)
                    label = result["label"].upper()
                    score = result["score"]
                    # נרמול לוגי של מודלים שונים
                    is_contradiction = (
                        label == "CONTRADICTION"
                        or label == "LABEL_0"
                    )

                    if (label == "CONTRADICTION" or label == "LABEL_0") and score > highest_score:

                        highest_score = score
                        best_old_fact = text_he
                        best_new_fact = new_fact_he
                        reason = "סתירה פנימית בתוך המשפט"

                        if score > 0.4:
                            found_contradiction = True

            # בנוסף, נשאיר בדיקת גיבוי קטנה למקרה שיש סתירה פנימית בתוך משפט החדש עצמו
         #   if not found_contradiction

            # החזרת התשובה המובנית לג'אווה
            if found_contradiction:
                response = (
                    f"CONTRADICTION_FOUND"
                    f"|SCORE:{highest_score:.4f}"
                    f"|REASON:{reason}"
                    f"|OLD:{best_old_fact}"
                    f"|NEW:{best_new_fact}\n"
                )
            else:
                response = "NO_CONTRADICTION|SCORE:0.0\n"

            print(f"[NLP] Sending response: {response.strip()}")
            conn.sendall(response.encode('utf-8'))

        #  בקשת וידאו ושפת גוף (קבלת נתיב לפריים)
        else:
            image_path = data
            print(f"[Body Language] Processing frame: {image_path}")
            import os
            print("repr =", repr(image_path))

            print("exists =", os.path.exists(image_path))

            print("isfile =", os.path.isfile(image_path))
            import numpy as np

            with open(image_path, "rb") as f:
                data = np.frombuffer(f.read(), np.uint8)
            
            image = cv2.imdecode(data, cv2.IMREAD_COLOR)

            print("image =", image)
            if image is not None:
                rgb_image = cv2.cvtColor(image, cv2.COLOR_BGR2RGB)

                # שימוש במנעול כדי שרק תהליכון אחד יגש ל-process של המודל בכל רגע
                with mediapipe_lock:
                    pose_results = pose.process(rgb_image)
                    face_results = face_mesh.process(rgb_image)

                all_data = []

                # חילוץ נקודות שלד הגוף
                if pose_results.pose_landmarks:
                    for lm in pose_results.pose_landmarks.landmark:
                        all_data.append(f"P:{lm.x:.4f},{lm.y:.4f},{lm.z:.4f},{lm.visibility:.4f}")

                # חילוץ נקודות תווי הפנים
                if face_results.multi_face_landmarks:
                    for face_landmarks in face_results.multi_face_landmarks:
                        for lm in face_landmarks.landmark:
                            all_data.append(f"F:{lm.x:.4f},{lm.y:.4f},{lm.z:.4f}")

                # בניית התשובה בפורמט שג'אווה מכירה
                if all_data:
                    response = ";".join(all_data) + "|DONE\n"
                else:
                    response = "NO_DATA\n"

                print(f"[Body Language] Sent {len(all_data)} landmarks successfully.")
            else:
                print(f"[Body Language] ⚠️ File not found: {image_path}")
                response = "FILE_NOT_FOUND\n"

            # שליחת נקודות הציון חזרה לג'אווה
            conn.sendall(response.encode('utf-8'))

    except Exception as e:
        import traceback

        traceback.print_exc()
        try:
            conn.sendall(f"ERROR:{e}\n".encode("utf-8"))
        except:
            pass

# אתחול והרצת השרת הראשי
server = socket.socket(socket.AF_INET, socket.SOCK_STREAM)
server.setsockopt(socket.SOL_SOCKET, socket.SO_REUSEADDR, 1)
server.bind(('localhost', 5050))
server.listen(15)  # תור רחב המאפשר זרם פריימים ובקשות NLP במקביל
print("\n--- Multi-threaded Master Server (NLP + MediaPipe) is running on port 5050 ---")

while True:
    conn, addr = server.accept()
    # פתיחת תהליכון עצמאי לחלוטין לכל פנייה מה-Java
    client_thread = threading.Thread(target=handle_client, args=(conn, addr))
    client_thread.start()