"""拆解官方 AntiSpoofPredict.predict 的预处理。"""
import sys

import cv2
import numpy as np
import torch
import torchvision.transforms as trans

REPO = r"E:\system\AndroidAI\_models_raw\repos\silentface\Silent-Face-Anti-Spoofing-master"
CKPT = r"E:\system\AndroidAI\_models_raw\2.7_80x80_MiniFASNetV2.pth"
CROP = r"E:\Dev\crops\image_T1.jpg_official.png"

sys.path.insert(0, REPO)
from src.anti_spoof_predict import AntiSpoofPredict  # noqa: E402

img = cv2.imread(CROP)
print("img", img.shape, img.dtype)

asp = AntiSpoofPredict(0)
prob = asp.predict(img, CKPT)
print("official predict:", np.round(prob, 3).tolist())

asp._load_model(CKPT)
asp.model.eval()

t = trans.ToTensor()(img)
print("ToTensor shape", tuple(t.shape), "min/max", float(t.min()), float(t.max()))
with torch.no_grad():
    logits = asp.model(t.unsqueeze(0)).numpy().reshape(-1)
print("manual logits (same model, ToTensor input):", np.round(logits, 3).tolist())

# 直接跑一次 forward 对比 predict 内部路径
t2 = trans.Compose([trans.ToTensor()])(img)
print("compose min/max", float(t2.min()), float(t2.max()))
