"""对比 PyTorch 原模型与导出 ONNX 的输出,并检查输入敏感性。"""
import sys

import numpy as np
import onnxruntime as ort
import torch

REPO = r"E:\system\AndroidAI\_models_raw\repos\silentface\Silent-Face-Anti-Spoofing-master"
CKPT = r"E:\system\AndroidAI\_models_raw\2.7_80x80_MiniFASNetV2.pth"
ONNX = r"E:\system\AndroidAI\app\src\main\assets\models\minifasnet.onnx"

sys.path.insert(0, REPO)
from src.model_lib.MiniFASNet import MiniFASNetV2  # noqa: E402

torch.manual_seed(0)
model = MiniFASNetV2(conv6_kernel=(5, 5))
state = torch.load(CKPT, map_location="cpu", weights_only=True)
model.load_state_dict({k.replace("module.", ""): v for k, v in state.items()})
model.eval()

sess = ort.InferenceSession(ONNX, providers=["CPUExecutionProvider"])

inputs = {
    "zeros": np.zeros((1, 3, 80, 80), np.float32),
    "ones": np.ones((1, 3, 80, 80), np.float32),
    "random": np.random.rand(1, 3, 80, 80).astype(np.float32),
}

for name, x in inputs.items():
    with torch.no_grad():
        t = model(torch.from_numpy(x)).numpy().reshape(-1)
    o = sess.run(None, {sess.get_inputs()[0].name: x})[0].reshape(-1)
    print(f"{name:7s} torch={np.round(t, 4).tolist()}")
    print(f"{'':7s} onnx ={np.round(o, 4).tolist()}  maxdiff={np.abs(t - o).max():.2e}")

# 权重统计
total_norm = sum(float(p.pow(2).sum()) for p in model.parameters())
print("weight norm:", total_norm)
