"""检查人脸旋转对 MiniFASNet 判定结果的影响。"""
import os
import sys

import cv2
import numpy as np
import onnxruntime as ort

sys.path.insert(0, r"E:\system\AndroidAI\tools\export")
from validate_pipeline import detect, MINIFAS, SCRFD  # noqa: E402

SRC = r"E:\system\AndroidAI\_models_raw\repos\silentface\Silent-Face-Anti-Spoofing-master\images\sample"


def probs(img, box, sess, scale=2.7):
    x1, y1, x2, y2 = box
    cx, cy = (x1 + x2) / 2, (y1 + y2) / 2
    bw, bh = (x2 - x1) * scale, (y2 - y1) * scale
    h, w = img.shape[:2]
    nx1, ny1 = max(0, int(cx - bw / 2)), max(0, int(cy - bh / 2))
    nx2, ny2 = min(w, int(cx + bw / 2)), min(h, int(cy + bh / 2))
    crop = cv2.resize(img[ny1:ny2, nx1:nx2], (80, 80)).astype(np.float32) / 255.0
    logits = sess.run(None, {sess.get_inputs()[0].name: crop.transpose(2, 0, 1)[None]})[0].reshape(-1)
    e = np.exp(logits - logits.max())
    return e / e.sum()


def main():
    scrfd = ort.InferenceSession(SCRFD, providers=["CPUExecutionProvider"])
    fas = ort.InferenceSession(MINIFAS, providers=["CPUExecutionProvider"])
    for name in ("image_T1.jpg", "image_F1.jpg", "image_F2.jpg"):
        img0 = cv2.imread(os.path.join(SRC, name))
        variants = [
            ("orig", img0),
            ("rot90cw", cv2.rotate(img0, cv2.ROTATE_90_CLOCKWISE)),
            ("rot90ccw", cv2.rotate(img0, cv2.ROTATE_90_COUNTERCLOCKWISE)),
        ]
        for label, img in variants:
            faces = detect(img, scrfd)
            if not faces:
                print(name, label, "no face")
                continue
            p = probs(img, faces[0][0], fas)
            print(f"{name:14s} {label:9s} faces={len(faces)} probs={np.round(p, 3).tolist()} argmax={int(p.argmax())}")


if __name__ == "__main__":
    main()
