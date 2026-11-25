"""保存 SCRFD 与官方 CropImage 两种裁剪结果,便于对比。"""
import os
import sys

import cv2
import numpy as np
import onnxruntime as ort

REPO = r"E:\system\AndroidAI\_models_raw\repos\silentface\Silent-Face-Anti-Spoofing-master"
sys.path.insert(0, r"E:\system\AndroidAI\tools\export")
sys.path.insert(0, REPO)
from validate_pipeline import detect, MINIFAS, SCRFD, liveness_score  # noqa: E402
from src.generate_patches import CropImage  # noqa: E402

SRC = os.path.join(REPO, "images", "sample")
OUT = r"E:\Dev\crops"
os.makedirs(OUT, exist_ok=True)


def main():
    scrfd = ort.InferenceSession(SCRFD, providers=["CPUExecutionProvider"])
    for name in ("image_T1.jpg", "image_F1.jpg"):
        img = cv2.imread(os.path.join(SRC, name))
        print(name, "shape", img.shape)
        faces = detect(img, scrfd)
        if not faces:
            print("  no face")
            continue
        box, kps, score = faces[0]
        print("  scrfd box", np.round(box).astype(int).tolist(), "kps", np.round(kps.reshape(5, 2)).astype(int).tolist())

        x1, y1, x2, y2 = np.round(box).astype(int)
        cv2.imwrite(os.path.join(OUT, f"{name}_scrfd.png"), img[y1:y2, x1:x2])

        bbox_xywh = [int(x1), int(y1), int(x2 - x1), int(y2 - y1)]
        crop = CropImage().crop(org_img=img, bbox=bbox_xywh, scale=2.7, out_w=80, out_h=80, crop=True)
        cv2.imwrite(os.path.join(OUT, f"{name}_official.png"), crop)

        # 放大版,方便肉眼看
        big = cv2.resize(crop, (320, 320), interpolation=cv2.INTER_NEAREST)
        cv2.imwrite(os.path.join(OUT, f"{name}_official_big.png"), big)


if __name__ == "__main__":
    main()
