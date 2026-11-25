"""用 PyTorch 原模型和官方 predict 分别跑同一张裁剪图。"""
import sys

import cv2
import numpy as np
import torch

REPO = r"E:\system\AndroidAI\_models_raw\repos\silentface\Silent-Face-Anti-Spoofing-master"
CKPT = r"E:\system\AndroidAI\_models_raw\2.7_80x80_MiniFASNetV2.pth"
CROPS = r"E:\Dev\crops"

sys.path.insert(0, REPO)
from src.model_lib.MiniFASNet import MiniFASNetV2  # noqa: E402

model = MiniFASNetV2(conv6_kernel=(5, 5))
state = torch.load(CKPT, map_location="cpu", weights_only=True)
model.load_state_dict({k.replace("module.", ""): v for k, v in state.items()})
model.eval()

img = cv2.imread(CROPS + r"\image_T1.jpg_official.png")
print("crop", img.shape, img.dtype, "mean", img.mean())

# 复刻官方 trans.ToTensor 的处理方式(numpy HWC uint8 -> CHW [0,1])
t = torch.from_numpy(img.transpose(2, 0, 1)).float() / 255.0
with torch.no_grad():
    logits = model(t.unsqueeze(0)).numpy().reshape(-1)
print("torch logits", np.round(logits, 3).tolist())

# 官方 AntiSpoofPredict.predict
from src.anti_spoof_predict import AntiSpoofPredict  # noqa: E402
asp = AntiSpoofPredict(0)
prob = asp.predict(img, CKPT)
print("official predict", np.round(prob, 3).tolist())
