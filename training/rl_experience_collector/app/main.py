from fastapi import FastAPI, HTTPException
from fastapi.middleware.cors import CORSMiddleware
from fastapi.exceptions import RequestValidationError
from fastapi.responses import JSONResponse
from fastapi import status, Request
from app.schemas import ExperienceSchema
from app.persistence import ExperienceWriter
from app.logger import logger

app = FastAPI(title="RL Experience Collector")

# Allow LB CORS
app.add_middleware(
    CORSMiddleware,
    allow_origins=["*"],
    allow_methods=["POST"],
    allow_headers=["*"],
)

@app.post("/experience", status_code=201)
async def collect_experience(exp: ExperienceSchema):
    """
    Endpoint to receive RL experiences from the load balancer.
    """
    try:
        ExperienceWriter.append(exp)
        logger.info("Experience recorded", extra={"action": exp.action, "reward": exp.reward})
        return {"status": "ok"}
    except Exception as e:
        logger.error("Error recording experience", exc_info=e)
        raise HTTPException(status_code=500, detail="Failed to record experience")

@app.get("/health")
async def health():
    return {"status": "healthy"}

@app.exception_handler(RequestValidationError)
async def validation_exception_handler(request: Request, exc: RequestValidationError):
    import logging
    logging.error(f"422 Error: {exc.errors()} Body: {exc.body}")
    return JSONResponse(
        status_code=status.HTTP_422_UNPROCESSABLE_ENTITY,
        content={"detail": exc.errors(), "body": exc.body},
    )