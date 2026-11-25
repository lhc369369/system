"""对比我们自己实例化的模型与官方 AntiSpoofPredict 内部模型。"""
import sys

import cv2
import numpy as np
import torch

REPO = r"E:\system\AndroidAI\_models_raw\repos\silentface\Silent-Face-Anti-Spoofing-master"
CKPT = r"E:\system\AndroidAI\_models_raw\2.7_80x80_MiniFASNetV2.pth"
CROP = r"E:\Dev\crops\image_T1.jpg_official.png"

sys.path.insert(0, REPO)
from src.anti_spoof_predict import AntiSpoofPredict  # noqa: E402
from src.model_lib.MiniFASNet import MiniFASNetV2  # noqa: E402

img = cv2.imread(CROP)
t = torch.from_numpy(img.transpose(2, 0, 1)).float() / 255.0
t = t.unsqueeze(0)

ours = MiniFASNetV2(conv6_kernel=(5, 5))
ours_state = torch.load(CKPT, map_location="cpu", weights_only=True)
ours.load_state_dict({k.replace("module.", ""): v for k, v in ours_state.items()})
ours.eval()

asp = AntiSpoofPredict(0)
asp._load_model(CKPT)
theirs = asp.model
theirs.eval()

print("kernel ours:", ours.conv_6_dw.conv.kernel_size, "theirs:", theirs.conv_6_dw.conv.kernel_size)
print("embedding ours:", ours.embedding_size, "theirs:", theirs.embedding_size)

sd_ours = ours.state_dict()
sd_theirs = theirs.state_dict()
diffs = [(k, float((sd_ours[k].float() - sd_theirs[k].float()).abs().max())) for k in sd_ours]
print("param keys:", len(sd_ours), len(sd_theirs))
print("max weight diff:", max(d for _, d in diffs))
print("top diff keys:", sorted(diffs, key=lambda x: -x[1])[:3])

with torch.no_grad():
    lo = ours(t).numpy().reshape(-1)
    lt = theirs(t).numpy().reshape(-1)
print("ours  logits", np.round(lo, 3).tolist())
print("theirs logits", np.round(lt, 3).tolist())
