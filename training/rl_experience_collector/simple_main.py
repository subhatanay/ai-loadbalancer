from fastapi import FastAPI

app = FastAPI(title="RL Experience Collector - Simple Test")

@app.get("/health")
async def health_check():
    return {"status": "ok", "message": "RL Experience Collector is running"}

@app.post("/experience")
async def collect_experience(data: dict):
    return {"status": "received", "data": data}
