"""把官方裁剪出的 80x80 图直接送进我们的 ONNX,对比 BGR/RGB。"""
import cv2
import numpy as np
import onnxruntime as ort

ONNX = "E:/system/AndroidAI/app/src/main/assets/models/minifasnet.onnx"
CROPS = "E:/Dev/crops"

sess = ort.InferenceSession(ONNX, providers=["CPUExecutionProvider"])
mine = ort.InferenceSession(
    "E:/system/AndroidAI/app/src/main/assets/models/minifasnet.onnx",
    providers=["CPUExecutionProvider"],
)

for f in ("image_T1.jpg_official.png", "image_F1.jpg_official.png"):
    img = cv2.imread(f"{CROPS}/{f}")
    print(f, img.shape if img is not None else None)
    for order in ("bgr", "rgb"):
        x = img if order == "bgr" else cv2.cvtColor(img, cv2.COLOR_BGR2RGB)
        t = x.astype(np.float32) / 255.0
        logits = sess.run(None, {sess.get_inputs()[0].name: t.transpose(2, 0, 1)[None]})[0].reshape(-1)
        p = np.exp(logits - logits.max())
        p /= p.sum()
        print(f"  {order}: logits={np.round(logits, 3).tolist()} probs={np.round(p, 3).tolist()}")
