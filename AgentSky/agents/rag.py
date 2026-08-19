"""写作资料向量库 — FAISS 检索，供 writer/setting/character/plot 检索参考素材"""

import re

def split_text_into_chunks(
        text: str,
        chunk_size: int = 512,
        overlap_sent_count: int = 2,
) -> list[str]:
    """
    中文切块：先分句聚合，优先保证句子完整，相邻块重叠尾部句子
    :param chunk_size: 单块最大字符数
    :param overlap_sent_count: 两块之间重叠保留的句子数量
    """
    if not text.strip():
        return []

    # 1.分句 + 清洗空白
    raw_segments = re.split(r'(?<=[。！？…\n])', text)
    sentences = []
    for seg in raw_segments:
        clean_seg = re.sub(r'\s+', ' ', seg).strip()
        if clean_seg:
            sentences.append(clean_seg)

    final_chunks = []
    buffer = []
    buffer_char_len = 0

    for sent in sentences:
        sent_len = len(sent)
        # 加上当前句子超出上限，先输出一块
        if buffer and buffer_char_len + sent_len > chunk_size:
            final_chunks.append("".join(buffer))
            # 保留末尾句子作为重叠
            buffer = buffer[-overlap_sent_count:]
            buffer_char_len = sum(len(s) for s in buffer)

        buffer.append(sent)
        buffer_char_len += sent_len

    # 剩余内容收尾
    if buffer:
        final_chunks.append("".join(buffer))

    return final_chunks


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

    def __init__(
        self,
        embed_model: str = "all-MiniLM-L6-v2",
        chunk_size: int = 512,
        overlap_sent_count: int = 2
    ):
        self.index = None
        self.embedder = None
        self.chunks: list[str] = []       # 切块片段
        self.meta: list[dict] = []         # 对应元信息
        self.chunk_size = chunk_size
        self.overlap_sent_count = overlap_sent_count


        if not _EMBED_AVAILABLE:
            return
        if os.getenv("RAG_ENABLED", "true").lower() in {"0", "false", "no", "off"}:
            print("  [RAG] 已禁用 (RAG_ENABLED=false)，跳过嵌入模型加载")
            return
        try:
            self.embedder = SentenceTransformer(embed_model)
        except Exception as e:
            print(f"  [RAG] 加载嵌入模型失败: {e}")

    def _build_index(self):
        """构建 FAISS 索引"""
        self.index = None
        if not self.chunks or self.embedder is None:
            return
        vecs = self.embedder.encode(self.chunks, normalize_embeddings=True)
        vecs = np.asarray(vecs, dtype=np.float32)
        dim = vecs.shape[1]
        self.index = faiss.IndexFlatIP(dim)
        self.index.add(vecs)

    def _load_single_file(self, file_text: str, source_name: str):
        """载入单个文件：自动切块并追加到库"""
        chunk_list = split_text_into_chunks(
        file_text,
        chunk_size=self.chunk_size,
        overlap_sent_count=self.overlap_sent_count
        )
        for idx, ck in enumerate(chunk_list):
            self.chunks.append(ck)
            self.meta.append({
                "source": source_name,
                "chunk_idx": idx
            })

    def ingest(self, documents: list[str]):
        """兼容旧接口：传入文本列表，自动切块入库"""
        self.chunks.clear()
        self.meta.clear()
        for idx, text in enumerate(documents):
            self._load_single_file(text, source_name=f"raw_input_{idx}")
        self._build_index()

    def ingest_from_folder(self, folder: str) -> tuple[int, int]:
        """读取文件夹所有txt，切块入库"""
        # 先清空旧数据
        self.chunks.clear()
        self.meta.clear()

        file_count = 0
        for path in sorted(Path(folder).glob("*.txt")):
            try:
                content = path.read_text(encoding="utf-8")
                self._load_single_file(content, path.name)
                file_count += 1
            except Exception as e:
                print(f"  [RAG] 读取失败 {path.name}: {e}")
        # 全部加载完成，一次性构建索引
        self._build_index()
        return file_count, len(self.chunks)

    def search(self, query: str, top_k: int = 3) -> list[dict]:
        if self.index is None or not self.chunks or self.embedder is None:
            return []
        q_vec = self.embedder.encode([query], normalize_embeddings=True).astype(np.float32)
        scores, idxs = self.index.search(q_vec, min(top_k, len(self.chunks)))

        result = []
        for j, chunk_id in enumerate(idxs[0]):
            if chunk_id < 0:
                continue
            m = self.meta[chunk_id]
            result.append({
                "content": self.chunks[chunk_id],
                "score": float(scores[0][j]),
                "source": m["source"],
                "chunk_idx": m["chunk_idx"]
            })
        return result


_store = None
_store_loaded = False


def get_store(chunk_size=512, overlap_sent_count=2) -> RagStore:
    """模块级单例 — 首次调用从 data/reference/ 载入素材；失败则返回空 store"""
    global _store, _store_loaded
    if _store_loaded:
        return _store

    folder = os.path.join(os.path.dirname(os.path.dirname(__file__)), "data", "reference")
    store = RagStore(chunk_size=chunk_size, overlap_sent_count=overlap_sent_count)
    file_num, chunk_num = 0, 0
    if os.path.isdir(folder):
        try:
            file_num, chunk_num = store.ingest_from_folder(folder)
        except Exception as e:
            print(f"  [RAG] 素材载入失败: {e}")
    print(f"  [RAG] 素材库就绪: {file_num}个文件，生成 {chunk_num} 个文本片段")
    _store = store
    _store_loaded = True
    return _store
