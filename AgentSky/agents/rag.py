"""标准 RAG 节点 — FAISS 向量检索 + LLM 生成"""

import numpy as np
from sentence_transformers import SentenceTransformer
import faiss
from langchain_core.messages import SystemMessage, HumanMessage


class RagStore:
    """向量存储与检索"""

    def __init__(self, embed_model: str = "all-MiniLM-L6-v2"):
        self.embedder = SentenceTransformer(embed_model)
        self.index = None
        self.docs: list[str] = []

    def ingest(self, documents: list[str]):
        """导入文档构建 FAISS 索引"""
        self.docs = list(documents)
        if not self.docs:
            return
        vecs = self.embedder.encode(self.docs, normalize_embeddings=True)
        self.index = faiss.IndexFlatIP(vecs.shape[1])
        self.index.add(vecs.astype(np.float32))

    def search(self, query: str, top_k: int = 3) -> list[dict]:
        """检索 top_k 最相关文档"""
        if self.index is None or not self.docs:
            return []
        q = self.embedder.encode([query], normalize_embeddings=True).astype(np.float32)
        scores, idxs = self.index.search(q, min(top_k, len(self.docs)))
        return [
            {"content": self.docs[i], "score": float(scores[0][j])}
            for j, i in enumerate(idxs[0]) if i >= 0
        ]


SYSTEM_PROMPT = "根据参考资料回答问题。资料不足时据实说明，不要编造。"


class RagAgent:
    """RAG 节点：检索 + 增强生成"""

    def __init__(self, model, store: RagStore):
        self.model = model
        self.store = store

    def invoke(self, state: dict) -> dict:
        query = state.get("rag_query", "")
        if not query:
            return {"rag_context": "", "rag_answer": "", "messages": []}

        results = self.store.search(query, top_k=3)
        context = "\n\n---\n\n".join(r["content"] for r in results)

        prompt = f"参考资料:\n{context}\n\n问题: {query}" if context else query
        resp = self.model.invoke([
            SystemMessage(content=SYSTEM_PROMPT),
            HumanMessage(content=prompt),
        ])

        return {
            "rag_context": context,
            "rag_answer": resp.content,
            "rag_sources": [r["content"][:200] for r in results],
            "messages": [f"[RagAgent] 检索{len(results)}篇 → 生成{len(resp.content)}字"],
        }
