"""检查 MiniFASNet 各层激活是否有响应,以及 BN 统计量。"""
import sys

import numpy as np
import torch

REPO = r"E:\system\AndroidAI\_models_raw\repos\silentface\Silent-Face-Anti-Spoofing-master"
CKPT = r"E:\system\AndroidAI\_models_raw\2.7_80x80_MiniFASNetV2.pth"

sys.path.insert(0, REPO)
from src.model_lib.MiniFASNet import MiniFASNetV2  # noqa: E402

model = MiniFASNetV2(conv6_kernel=(5, 5))
state = torch.load(CKPT, map_location="cpu", weights_only=True)
model.load_state_dict({k.replace("module.", ""): v for k, v in state.items()})
model.eval()

print("bn.running_mean[:5]", model.bn.running_mean[:5].tolist())
print("bn.running_var[:5] ", model.bn.running_var[:5].tolist())
print("bn.weight[:5]      ", model.bn.weight[:5].tolist())
print("bn.bias[:5]        ", model.bn.bias[:5].tolist())

x0 = np.zeros((1, 3, 80, 80), np.float32)
x1 = np.random.rand(1, 3, 80, 80).astype(np.float32)

outs = {}


def hook(name):
    def f(module, inp, out):
        outs.setdefault(name, []).append(out.detach().numpy().reshape(-1))
    return f


names = ["conv1", "conv2_dw", "conv_23", "conv_3", "conv_34", "conv_4", "conv_45", "conv_5",
         "conv_6_sep", "conv_6_dw", "bn", "prob"]
for n in names:
    getattr(model, n).register_forward_hook(hook(n))

with torch.no_grad():
    model(torch.from_numpy(x0))
    model(torch.from_numpy(x1))

print("\nlayer            zeros(std)  random(std)  diff_mean")
for n in names:
    a, b = outs[n][0], outs[n][1]
    print(f"{n:14s} {a.std():10.4f} {b.std():11.4f} {np.abs(a - b).mean():10.5f}")
