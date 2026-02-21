from __future__ import annotations

import os
from typing import List

from fastapi import FastAPI, Header, HTTPException
from pydantic import BaseModel
from sentence_transformers import CrossEncoder
import torch


class Candidate(BaseModel):
    id: str
    documentTitle: str
    sectionPath: str | None = None
    text: str


class RerankRequest(BaseModel):
    query: str
    candidates: List[Candidate]


class ScoreItem(BaseModel):
    id: str
    score: float


class RerankResponse(BaseModel):
    scores: List[ScoreItem]


MODEL_NAME = os.getenv("CROSS_ENCODER_MODEL", "cross-encoder/ms-marco-MiniLM-L6-v2")
MAX_TEXT_CHARS = int(os.getenv("CROSS_ENCODER_MAX_TEXT_CHARS", "700"))
MAX_CANDIDATES = int(os.getenv("CROSS_ENCODER_MAX_CANDIDATES", "50"))
API_KEY = os.getenv("CROSS_ENCODER_API_KEY", "").strip()

app = FastAPI(title="DochiBot Cross-Encoder Service")
model = CrossEncoder(MODEL_NAME, activation_fn=torch.nn.Sigmoid())


def clamp(value: float, low: float = 0.0, high: float = 1.0) -> float:
    return max(low, min(high, value))


@app.get("/health")
def health() -> dict:
    return {"ok": True, "model": MODEL_NAME}


@app.post("/rerank", response_model=RerankResponse)
def rerank(
    payload: RerankRequest,
    x_api_key: str | None = Header(default=None),
    authorization: str | None = Header(default=None),
) -> RerankResponse:
    bearer = None
    if authorization and authorization.lower().startswith("bearer "):
        bearer = authorization[7:].strip()
    provided = x_api_key or bearer

    if API_KEY and provided != API_KEY:
        raise HTTPException(status_code=401, detail="invalid api key")

    if not payload.candidates:
        return RerankResponse(scores=[])
    if len(payload.candidates) > MAX_CANDIDATES:
        raise HTTPException(
            status_code=400, detail=f"candidates must be <= {MAX_CANDIDATES}"
        )

    pairs = []
    for c in payload.candidates:
        doc = c.text.replace("\n", " ")[:MAX_TEXT_CHARS]
        pairs.append((payload.query, doc))

    raw_scores = model.predict(pairs)
    scores = [
        ScoreItem(id=c.id, score=clamp(float(score)))
        for c, score in zip(payload.candidates, raw_scores)
    ]
    return RerankResponse(scores=scores)
