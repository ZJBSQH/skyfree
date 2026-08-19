from fastapi import FastAPI
from databasep2 import Base, engine
from skyfree import router as sky_router


Base.metadata.create_all(bind=engine)

app = FastAPI(title="Freesky Agent 服务")

app.include_router(sky_router, prefix="/sky", tags=["Sky Management"])



@app.get("/")
def test():
    return {"status": "ok"}
