import socket
import cv2
import mediapipe as mp

mp_pose = mp.solutions.pose
mp_face_mesh = mp.solutions.face_mesh
pose = mp_pose.Pose(static_image_mode=True)
face_mesh = mp_face_mesh.FaceMesh(static_image_mode=True, max_num_faces=1)

server = socket.socket(socket.AF_INET, socket.SOCK_STREAM)
server.bind(('localhost', 5050))
server.listen(1)
print("Python Server is waiting for Java...")

while True:
    conn, addr = server.accept()
    data = conn.recv(1024).decode('utf-8').strip()

    if data:
        image = cv2.imread(data)
        if image is not None:
            rgb_image = cv2.cvtColor(image, cv2.COLOR_BGR2RGB)
            pose_results = pose.process(rgb_image)
            face_results = face_mesh.process(rgb_image)

            all_data = []

            if pose_results.pose_landmarks:
                for lm in pose_results.pose_landmarks.landmark:
                    all_data.append(f"P:{lm.x:.4f},{lm.y:.4f},{lm.z:.4f},{lm.visibility:.4f}")

            if face_results.multi_face_landmarks:
                for face_landmarks in face_results.multi_face_landmarks:
                    for lm in face_landmarks.landmark:
                        all_data.append(f"F:{lm.x:.4f},{lm.y:.4f},{lm.z:.4f}")

            if all_data:
                response = ";".join(all_data) + "|DONE\n"  # DONE כדי שג'אווה תזהה
            else:
                response = "NO_DATA\n"

            # conn.sendall כשהתמונה נמצאת
            conn.sendall(response.encode('utf-8'))
            print(f"Sent {len(all_data)} landmarks")

        else:
            conn.sendall("FILE_NOT_FOUND\n".encode('utf-8'))

    conn.close()