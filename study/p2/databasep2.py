from sqlalchemy import create_engine
from sqlalchemy.orm import sessionmaker,declarative_base


DATABASE_URL = "sqlite:///./skyfree.db"
engine = create_engine(DATABASE_URL)
SessionLocal = sessionmaker(autocommit = False,autoflush = False,bind = engine)
Base = declarative_base()

def get_db():
    d = SessionLocal()
    try:
        yield d
    finally:
        d.close()