"""截获官方 predict 内部的模型输出。"""
import sys

import cv2
import numpy as np

REPO = r"E:\system\AndroidAI\_models_raw\repos\silentface\Silent-Face-Anti-Spoofing-master"
CKPT = r"E:\system\AndroidAI\_models_raw\2.7_80x80_MiniFASNetV2.pth"
CROP = r"E:\Dev\crops\image_T1.jpg_official.png"

sys.path.insert(0, REPO)
import src.anti_spoof_predict as ap  # noqa: E402
from src.model_lib import MiniFASNet as mf  # noqa: E402

orig_softmax = ap.F.softmax


def spy(x, *args, **kwargs):
    print("pre-softmax output:", x.detach().numpy().reshape(-1))
    return orig_softmax(x, *args, **kwargs)


ap.F.softmax = spy

orig_forward = mf.MiniFASNet.forward


def fwd(self, x):
    print("forward input stats: shape", tuple(x.shape), "min", float(x.min()), "max", float(x.max()), "mean", float(x.mean()))
    return orig_forward(self, x)


mf.MiniFASNet.forward = fwd

img = cv2.imread(CROP)
m = ap.AntiSpoofPredict(0)
print("result:", np.round(m.predict(img, CKPT), 3).tolist())
