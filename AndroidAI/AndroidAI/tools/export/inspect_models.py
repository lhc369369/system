"""打印三个 ONNX 模型的输入输出名称与形状。"""
import os

import onnxruntime as ort

MODELS = {
    "scrfd": r"E:\system\AndroidAI\app\src\main\assets\models\scrfd.onnx",
    "edgeface": r"E:\system\AndroidAI\app\src\main\assets\models\edgeface.onnx",
    "minifasnet": r"E:\system\AndroidAI\app\src\main\assets\models\minifasnet.onnx",
}

for name, path in MODELS.items():
    if not os.path.exists(path):
        print(f"{name}: 缺失 {path}")
        continue
    sess = ort.InferenceSession(path, providers=["CPUExecutionProvider"])
    print(f"\n{name}  ({os.path.getsize(path) / 1e6:.1f} MB)")
    for i in sess.get_inputs():
        print("  in :", i.name, i.shape, i.type)
    for o in sess.get_outputs():
        print("  out:", o.name, o.shape, o.type)
