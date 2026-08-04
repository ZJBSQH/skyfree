"""写作资料向量库 — FAISS 检索，供 writer/setting/character/plot 检索参考素材"""

import os
from pathlib import Path

try:
    import numpy as np
    import faiss
    from sentence_transformers import SentenceTransformer
    _EMBED_AVAILABLE = True
except ImportError:
    _EMBED_AVAILABLE = False
    np = None


class RagStore:
    """向量存储与检索：每个 .txt 文件作为一条文档"""

    def __init__(self, embed_model: str = "all-MiniLM-L6-v2"):
        self.docs: list[str] = []
        self.index = None
        self.embedder = None
        if _EMBED_AVAILABLE:
            try:
                self.embedder = SentenceTransformer(embed_model)
            except Exception as e:
                print(f"  [RAG] 加载嵌入模型失败: {e}")

    def ingest(self, documents: list[str]):
        self.docs = list(documents)
        if not self.docs or self.embedder is None:
            return
        vecs = self.embedder.encode(self.docs, normalize_embeddings=True)
        self.index = faiss.IndexFlatIP(vecs.shape[1])
        self.index.add(np.asarray(vecs, dtype=np.float32))

    def ingest_from_folder(self, folder: str) -> int:
        docs = []
        for path in sorted(Path(folder).glob("*.txt")):
            docs.append(path.read_text(encoding="utf-8"))
        self.ingest(docs)
        return len(docs)

    def search(self, query: str, top_k: int = 3) -> list[dict]:
        if self.index is None or not self.docs or self.embedder is None:
            return []
        q = self.embedder.encode([query], normalize_embeddings=True).astype(np.float32)
        scores, idxs = self.index.search(q, min(top_k, len(self.docs)))
        return [
            {"content": self.docs[i], "score": float(scores[0][j])}
            for j, i in enumerate(idxs[0]) if i >= 0
        ]


_store = None
_store_loaded = False


def get_store() -> RagStore:
    """模块级单例 — 首次调用从 data/reference/ 载入素材；失败则返回空 store"""
    global _store, _store_loaded
    if _store_loaded:
        return _store
    _store_loaded = True

    folder = os.path.join(os.path.dirname(os.path.dirname(__file__)), "data", "reference")
    store = RagStore()
    n = 0
    if os.path.isdir(folder):
        try:
            n = store.ingest_from_folder(folder)
        except Exception as e:
            print(f"  [RAG] 素材载入失败: {e}")
    print(f"  [RAG] 素材库就绪: {n}篇文档")
    _store = store
    _store_loaded = True
    return _store
