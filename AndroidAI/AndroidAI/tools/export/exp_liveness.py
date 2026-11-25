"""排查 MiniFASNet 输入:比较 BGR / RGB、裁剪 scale 对输出概率的影响。"""
import sys

import cv2
import numpy as np
import onnxruntime as ort

sys.path.insert(0, r"E:\system\AndroidAI\tools\export")
from validate_pipeline import detect, MINIFAS, SCRFD  # noqa: E402


def probs_for(img, box, sess, scale, order):
    x1, y1, x2, y2 = box
    cx, cy = (x1 + x2) / 2, (y1 + y2) / 2
    bw, bh = (x2 - x1) * scale, (y2 - y1) * scale
    h, w = img.shape[:2]
    nx1, ny1 = max(0, int(cx - bw / 2)), max(0, int(cy - bh / 2))
    nx2, ny2 = min(w, int(cx + bw / 2)), min(h, int(cy + bh / 2))
    crop = img[ny1:ny2, nx1:nx2]
    crop = cv2.resize(crop, (80, 80))
    if order == "rgb":
        crop = cv2.cvtColor(crop, cv2.COLOR_BGR2RGB)
    x = crop.astype(np.float32) / 255.0
    inp = x.transpose(2, 0, 1)[None]
    logits = sess.run(None, {sess.get_inputs()[0].name: inp})[0].reshape(-1)
    e = np.exp(logits - logits.max())
    return e / e.sum()


def main(paths):
    scrfd = ort.InferenceSession(SCRFD, providers=["CPUExecutionProvider"])
    fas = ort.InferenceSession(MINIFAS, providers=["CPUExecutionProvider"])
    for path in paths:
        img = cv2.imread(path)
        faces = detect(img, scrfd)
        if not faces:
            print(path, "no face")
            continue
        box = faces[0][0]
        print(f"\n{path.split(chr(92))[-1]}")
        for scale in (2.0, 2.7):
            for order in ("bgr", "rgb"):
                p = probs_for(img, box, fas, scale, order)
                print(f"  scale={scale} {order}: {np.round(p, 3).tolist()}")


if __name__ == "__main__":
    main(sys.argv[1:])
