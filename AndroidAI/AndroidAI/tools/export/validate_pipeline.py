"""电脑端验证:SCRFD 检测 -> 关键点对齐 -> EdgeFace 特征 -> MiniFASNet 活体。

用法: python -I validate_pipeline.py <图片路径> [<图片路径> ...]
"""
import sys

import cv2
import numpy as np
import onnxruntime as ort

MODELS = r"E:\system\AndroidAI\app\src\main\assets\models"
SCRFD = MODELS + r"\scrfd.onnx"
EDGEFACE = MODELS + r"\edgeface.onnx"
MINIFAS = MODELS + r"\minifasnet.onnx"

INPUT_SIZE = 640
STRIDES = (8, 16, 32)
NUM_ANCHORS = 2
ARC_TEMPLATE = np.array(
    [[38.2946, 51.6963], [73.5318, 51.5014], [56.0252, 71.7366],
     [41.5493, 92.3655], [70.7299, 92.2041]], dtype=np.float32
)


def distance2bbox(points, distance):
    return np.stack([
        points[:, 0] - distance[:, 0],
        points[:, 1] - distance[:, 1],
        points[:, 0] + distance[:, 2],
        points[:, 1] + distance[:, 3],
    ], axis=1)


def distance2kps(points, distance):
    out = []
    for i in range(0, distance.shape[1], 2):
        out.append(points[:, 0] + distance[:, i])
        out.append(points[:, 1] + distance[:, i + 1])
    return np.stack(out, axis=1)


def anchor_centers():
    centers = []
    for stride in STRIDES:
        fm = INPUT_SIZE // stride
        y, x = np.mgrid[:fm, :fm]
        c = np.stack([x, y], axis=-1).astype(np.float32).reshape(-1, 2) * stride
        c = np.repeat(c, NUM_ANCHORS, axis=0)
        centers.append(c)
    return centers


def detect(img_bgr, sess, conf=0.5, nms=0.4):
    h, w = img_bgr.shape[:2]
    ratio = h / w
    if ratio > 1.0:
        new_h, new_w = INPUT_SIZE, int(INPUT_SIZE / ratio)
    else:
        new_w, new_h = INPUT_SIZE, int(INPUT_SIZE * ratio)
    det_scale = new_h / h

    resized = cv2.resize(img_bgr, (new_w, new_h))
    canvas = np.zeros((INPUT_SIZE, INPUT_SIZE, 3), np.uint8)
    canvas[:new_h, :new_w] = resized

    blob = cv2.dnn.blobFromImage(
        canvas, 1.0 / 128, (INPUT_SIZE, INPUT_SIZE),
        (127.5, 127.5, 127.5), swapRB=True
    )
    outs = sess.run(None, {sess.get_inputs()[0].name: blob})
    scores, bboxes, kpss = outs[0:3], outs[3:6], outs[6:9]

    centers = anchor_centers()
    all_boxes, all_kps, all_scores = [], [], []
    for idx, stride in enumerate(STRIDES):
        sc = scores[idx].reshape(-1)
        keep = sc > conf
        if not np.any(keep):
            continue
        bb = distance2bbox(centers[idx], bboxes[idx] * stride)[keep]
        kp = distance2kps(centers[idx], kpss[idx] * stride)[keep]
        all_boxes.append(bb / det_scale)
        all_kps.append(kp / det_scale)
        all_scores.append(sc[keep])

    if not all_boxes:
        return []

    boxes = np.vstack(all_boxes)
    kps = np.vstack(all_kps)
    scores = np.hstack(all_scores)

    nms_boxes = [[float(b[0]), float(b[1]), float(b[2] - b[0]), float(b[3] - b[1])] for b in boxes]
    idx = cv2.dnn.NMSBoxes(nms_boxes, scores.tolist(), conf, nms)
    if len(idx) == 0:
        return []
    idx = np.array(idx).reshape(-1)
    return [(boxes[i], kps[i], float(scores[i])) for i in idx]


def align(img_bgr, kps):
    src = np.asarray(kps, dtype=np.float32).reshape(5, 2)
    tf = cv2.estimateAffinePartial2D(src, ARC_TEMPLATE, method=cv2.LMEDS)[0]
    return cv2.warpAffine(img_bgr, tf, (112, 112), borderValue=0.0)


def edgeface_embed(face_bgr, sess):
    rgb = cv2.cvtColor(face_bgr, cv2.COLOR_BGR2RGB).astype(np.float32) / 255.0
    inp = ((rgb - 0.5) / 0.5).transpose(2, 0, 1)[None]
    out = sess.run(None, {sess.get_inputs()[0].name: inp})[0]
    vec = out.reshape(-1)
    return vec / (np.linalg.norm(vec) + 1e-9)


def liveness_score(img_bgr, box, sess, scale=2.7):
    x1, y1, x2, y2 = box
    cx, cy = (x1 + x2) / 2, (y1 + y2) / 2
    bw, bh = (x2 - x1) * scale, (y2 - y1) * scale
    h, w = img_bgr.shape[:2]
    nx1, ny1 = max(0, int(cx - bw / 2)), max(0, int(cy - bh / 2))
    nx2, ny2 = min(w, int(cx + bw / 2)), min(h, int(cy + bh / 2))
    crop = img_bgr[ny1:ny2, nx1:nx2]
    if crop.size == 0:
        return None
    crop = cv2.resize(crop, (80, 80))
    # 官方 MiniFASNet 的 ToTensor 去掉了 /255,模型输入是 0-255 的 BGR 原值
    inp = crop.astype(np.float32).transpose(2, 0, 1)[None]
    logits = sess.run(None, {sess.get_inputs()[0].name: inp})[0].reshape(-1)
    e = np.exp(logits - logits.max())
    prob = e / e.sum()
    return prob


def main(paths):
    scrfd = ort.InferenceSession(SCRFD, providers=["CPUExecutionProvider"])
    edge = ort.InferenceSession(EDGEFACE, providers=["CPUExecutionProvider"])
    fas = ort.InferenceSession(MINIFAS, providers=["CPUExecutionProvider"])

    for path in paths:
        img = cv2.imread(path)
        if img is None:
            print(f"{path}: 读不到")
            continue
        faces = detect(img, scrfd)
        print(f"\n== {path}  {img.shape[1]}x{img.shape[0]}  检出 {len(faces)} 张脸")
        for i, (box, kps, score) in enumerate(faces[:3]):
            aligned = align(img, kps)
            emb = edgeface_embed(aligned, edge)
            prob = liveness_score(img, box, fas)
            print(f"  人脸{i}: score={score:.3f} bbox={np.round(box).astype(int).tolist()}")
            print(f"          embedding dim={emb.shape[0]} L2={np.linalg.norm(emb):.3f} norm[0:3]={np.round(emb[:3], 3).tolist()}")
            if prob is not None:
                print(f"          活体分类 probs={np.round(prob, 3).tolist()}  argmax={int(prob.argmax())} (1=真人)")


if __name__ == "__main__":
    main(sys.argv[1:])
