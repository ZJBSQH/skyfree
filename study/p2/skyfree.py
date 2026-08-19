
# 2. 第三方库 fastapi、sqlalchemy
from fastapi import APIRouter, HTTPException, Depends
from sqlalchemy.orm import Session
# 3. 本地自定义文件
from databasep2 import get_db
from modelp2 import SkyItem
from schemasp2 import SkyOut, SkyCreate, SkyUpdate

router = APIRouter()

@router.post("/", response_model=SkyOut)
def create_sky(sky: SkyCreate, db: Session = Depends(get_db)):
    db_sky = SkyItem(**sky.dict())
    db.add(db_sky)
    db.commit()
    db.refresh(db_sky)
    return db_sky

@router.get("/", response_model=list[SkyOut])
def read_skies(skip: int = 0, limit: int = 10, db: Session = Depends(get_db)):
    return db.query(SkyItem).offset(skip).limit(limit).all()

@router.get("/{sky_id}", response_model=SkyOut)
def read_sky(sky_id: int, db: Session = Depends(get_db)):
    sky = db.query(SkyItem).filter(SkyItem.id == sky_id).first()
    if not sky:
        raise HTTPException(status_code=404, detail="Sky not found")
    return sky

@router.put("/{sky_id}", response_model=SkyOut)
def update_sky(sky_id: int, sky: SkyUpdate, db: Session = Depends(get_db)):
    db_sky = db.query(SkyItem).filter(SkyItem.id == sky_id).first()
    if not db_sky:
        raise HTTPException(status_code=404, detail="Sky not found")
    for key, value in sky.dict(exclude_unset=True).items():
        setattr(db_sky, key, value)
    db.commit()
    db.refresh(db_sky)
    return db_sky

@router.delete("/{sky_id}",response_model=dict)
def delete_sky(sky_id: int, db: Session = Depends(get_db)):
    db_sky = db.query(SkyItem).filter(SkyItem.id == sky_id).first()
    if not db_sky:
        raise HTTPException(status_code=404, detail="Sky not found")
    db.delete(db_sky)
    db.commit()
    return {"detail": "Sky deleted"}