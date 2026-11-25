"""把 EdgeFace-xs (gamma=0.6) PyTorch 权重导出为 ONNX,输入 1x3x112x112。"""
import os
import sys

import torch

REPO = r"E:\system\AndroidAI\_models_raw\repos\edgeface\edgeface-main"
CKPT = r"E:\system\AndroidAI\_models_raw\edgeface_xs_gamma_06.pt"
OUT = r"E:\system\AndroidAI\app\src\main\assets\models\edgeface.onnx"

sys.path.insert(0, REPO)
from backbones import get_model  # noqa: E402

def main():
    model = get_model("edgeface_xs_gamma_06")
    state = torch.load(CKPT, map_location="cpu", weights_only=True)
    if isinstance(state, dict) and "state_dict" in state:
        state = state["state_dict"]
    model.load_state_dict({k.replace("module.", ""): v for k, v in state.items()})
    model.eval()

    dummy = torch.randn(1, 3, 112, 112)
    with torch.no_grad():
        emb = model(dummy)
    print("embedding shape:", tuple(emb.shape))

    torch.onnx.export(
        model,
        dummy,
        OUT,
        input_names=["input"],
        output_names=["embedding"],
        opset_version=12,
        dynamo=False,
    )
    print("saved:", OUT, os.path.getsize(OUT), "bytes")


if __name__ == "__main__":
    main()
