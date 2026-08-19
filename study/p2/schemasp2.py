
from pydantic import BaseModel
from typing import Optional

class SkyCreate(BaseModel):
    title: str
    idea: Optional[str] = None
    novel_name: Optional[str] = None
    word_count: Optional[int] = None
    description: Optional[str] = None
    status: Optional[str] = "pending"

class SkyUpdate(BaseModel):
    title: Optional[str] = None
    idea: Optional[str] = None
    novel_name: Optional[str] = None
    word_count: Optional[int] = None
    description: Optional[str] = None
    status: Optional[str] = None

class SkyOut(SkyCreate):
    id: int
    class Config:
       from_attributes = True
