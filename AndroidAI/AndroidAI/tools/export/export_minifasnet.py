"""把 MiniFASNetV2 (2.7_80x80) PyTorch 权重导出为 ONNX,输入 1x3x80x80。

该权重由 parse_model_name("2.7_80x80_MiniFASNetV2.pth") 决定:
scale=2.7, 输入 80x80, conv6_kernel=get_kernel(80,80)=(5,5)。
输出 3 类 logits,类别顺序沿用官方: index 1 = 真人, 0/2 = 攻击样本。
"""
import os
import sys

import torch

REPO = r"E:\system\AndroidAI\_models_raw\repos\silentface\Silent-Face-Anti-Spoofing-master"
CKPT = r"E:\system\AndroidAI\_models_raw\2.7_80x80_MiniFASNetV2.pth"
OUT = r"E:\system\AndroidAI\app\src\main\assets\models\minifasnet.onnx"

sys.path.insert(0, REPO)
from src.model_lib.MiniFASNet import MiniFASNetV2  # noqa: E402

def main():
    model = MiniFASNetV2(conv6_kernel=(5, 5))
    state = torch.load(CKPT, map_location="cpu", weights_only=True)
    model.load_state_dict({k.replace("module.", ""): v for k, v in state.items()})
    model.eval()

    dummy = torch.randn(1, 3, 80, 80)
    with torch.no_grad():
        logits = model(dummy)
    print("logits shape:", tuple(logits.shape))

    torch.onnx.export(
        model,
        dummy,
        OUT,
        input_names=["input"],
        output_names=["logits"],
        opset_version=12,
        dynamo=False,
    )
    print("saved:", OUT, os.path.getsize(OUT), "bytes")


if __name__ == "__main__":
    main()
