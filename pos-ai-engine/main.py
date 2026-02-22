import os
import io
import torch
import clip
import faiss
import numpy as np
from PIL import Image
from fastapi import FastAPI, File, UploadFile, Form
from fastapi.responses import JSONResponse
from fastapi.middleware.cors import CORSMiddleware
import uvicorn

# 1. 初始化 FastAPI 实例
app = FastAPI(title="lkPOS AI 视觉引擎", version="1.0")

# 2. 配置跨域 (CORS) - 允许 Vue 前端直接跨域调用
app.add_middleware(
    CORSMiddleware,
    allow_origins=["*"],
    allow_credentials=True,
    allow_methods=["*"],
    allow_headers=["*"],
)

# 全局变量
device = "cuda" if torch.cuda.is_available() else "cpu"
model_clip = None
preprocess = None
index = None
metadata = None

@app.on_event("startup")
async def load_resources():
    """服务启动时，自动加载模型和本地特征库"""
    global model_clip, preprocess, index, metadata

    print("⏳ 正在加载 CLIP 视觉大模型...")
    model_clip, preprocess = clip.load("ViT-B/32", device=device)

    print("⏳ 正在加载 FAISS 向量数据库...")
    if os.path.exists("products.index") and os.path.exists("metadata.npy"):
        index = faiss.read_index("products.index")
        metadata = np.load("metadata.npy", allow_pickle=True)
        print(f"✅ 成功加载本地商品库，当前共有 {len(metadata)} 个商品！")
    else:
        print("⚠️ 未找到本地 products.index，正在为你初始化一个全新的空数据库...")
        index = faiss.IndexFlatIP(512)
        metadata = np.array([])
    print("🚀 AI 引擎启动完毕！准备接收前台收银指令...")

@app.post("/api/recognize")
async def recognize_product(file: UploadFile = File(...)):
    """核心接口：接收前台图片，返回商品条码"""
    try:
        if len(metadata) == 0:
            return {"code": 404, "msg": "当前商品库为空，请先调用 learn 接口录入商品"}

        contents = await file.read()
        image = Image.open(io.BytesIO(contents)).convert("RGB")
        img_input = preprocess(image).unsqueeze(0).to(device)

        with torch.no_grad():
            query_feat = model_clip.encode_image(img_input)
            query_feat /= query_feat.norm(dim=-1, keepdim=True)
            query_feat_np = query_feat.cpu().numpy().astype('float32')

        D, I = index.search(query_feat_np, k=1)
        best_score = float(D[0][0])

        if best_score >= 0.60:
            match = metadata[I[0][0]]
            return {
                "code": 200,
                "msg": "success",
                "data": {
                    "barcode": match['barcode'],
                    "name": match['name'],
                    "score": round(best_score, 4)
                }
            }
        else:
            return {"code": 404, "msg": "unknown_product", "data": {"score": round(best_score, 4)}}

    except Exception as e:
        return JSONResponse(status_code=500, content={"code": 500, "msg": f"AI 推理异常: {str(e)}"})

@app.post("/api/learn")
async def learn_new_product(file: UploadFile = File(...), barcode: str = Form(...), name: str = Form(...)):
    """自进化接口：接收新商品图片并立即存入 FAISS 库"""
    try:
        contents = await file.read()
        image = Image.open(io.BytesIO(contents)).convert("RGB")
        img_input = preprocess(image).unsqueeze(0).to(device)

        with torch.no_grad():
            features = model_clip.encode_image(img_input)
            features /= features.norm(dim=-1, keepdim=True)

        # 存入 FAISS
        index.add(features.cpu().numpy().astype('float32'))

        # 更新 metadata
        global metadata
        new_item = {"barcode": barcode, "name": name}
        meta_list = metadata.tolist() if isinstance(metadata, np.ndarray) and metadata.size > 0 else list(metadata)
        meta_list.append(new_item)
        metadata = np.array(meta_list, dtype=object)

        # 将最新记忆写入硬盘
        faiss.write_index(index, "products.index")
        np.save("metadata.npy", metadata)

        return {"code": 200, "msg": f"AI 已成功学习新商品: {name}"}
    except Exception as e:
        return JSONResponse(status_code=500, content={"code": 500, "msg": f"学习模块异常: {str(e)}"})

@app.delete("/api/product/{barcode}")
async def forget_product(barcode: str):
    """遗忘接口：从特征库中彻底删除指定商品"""
    global index, metadata
    try:
        if len(metadata) == 0:
            return {"code": 404, "msg": "记忆库为空"}

        # 1. 查找要删除的商品在数组中的位置
        target_idx = -1
        for i, item in enumerate(metadata):
            if item.get('barcode') == barcode:
                target_idx = i
                break

        if target_idx == -1:
            return {"code": 404, "msg": "AI 记忆库中不存在此商品，可能之前未录入图片"}

        # 2. 提取当前库里的所有特征向量
        all_vectors = np.array([index.reconstruct(i) for i in range(index.ntotal)])

        # 3. 物理剔除目标向量和对应的基本信息
        all_vectors = np.delete(all_vectors, target_idx, axis=0)
        metadata = np.delete(metadata, target_idx, axis=0)

        # 4. 瞬间重建 FAISS 库
        index.reset()
        if len(all_vectors) > 0:
            index.add(all_vectors)

        # 5. 覆盖保存到硬盘，确保永久遗忘
        faiss.write_index(index, "products.index")
        np.save("metadata.npy", metadata)

        return {"code": 200, "msg": f"AI 已彻底遗忘条码为 {barcode} 的商品"}

    except Exception as e:
        return JSONResponse(status_code=500, content={"code": 500, "msg": f"遗忘模块异常: {str(e)}"})


if __name__ == "__main__":
    # 直接在代码里配置启动参数
    uvicorn.run("main:app", host="0.0.0.0", port=8000, reload=True)