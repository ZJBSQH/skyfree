from sqlalchemy import  Column, Integer, String
from databasep2 import  Base

class SkyItem(Base):
    __tablename__ = "skyfree"
    id = Column(Integer, primary_key=True, index=True)
    title = Column(String, index=True)
    idea = Column(String, index=True)
    novel_name = Column(String, index=True)
    word_count = Column(Integer, index=True)
    description = Column(String, index=True)
    status = Column(String, index=True)