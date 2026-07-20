"""
FastAPI Microservice — AI Demand Forecast
=========================================
Endpoints:
  GET  /health          — health check
  POST /forecast/prophet — AI Prophet + Weather forecast
"""

from __future__ import annotations

import logging
import os
import time

from fastapi import FastAPI, HTTPException, Request
from fastapi.middleware.cors import CORSMiddleware
from fastapi.responses import JSONResponse

from models.prophet_model import run_prophet_forecast
from schemas import ForecastRequest, HealthResponse, ProphetForecastResponse

# ─── Logging ──────────────────────────────────────────────────────────────────
logging.basicConfig(
    level=os.getenv("LOG_LEVEL", "INFO"),
    format="%(asctime)s [%(levelname)s] %(name)s — %(message)s",
)
logger = logging.getLogger(__name__)

# ─── App ──────────────────────────────────────────────────────────────────────
app = FastAPI(
    title="Inventory AI — Forecast Microservice",
    description="Prophet + Weather-based demand forecasting for F&B inventory management",
    version="1.0.0",
    docs_url="/docs",
    redoc_url=None,
)

# CORS: chỉ cho phép từ Spring Boot backend (cấu hình qua env)
_allowed_origins = os.getenv("ALLOWED_ORIGINS", "http://localhost:8080").split(",")
app.add_middleware(
    CORSMiddleware,
    allow_origins=_allowed_origins,
    allow_methods=["GET", "POST"],
    allow_headers=["Content-Type", "Authorization"],
)


# ─── Request timing middleware ────────────────────────────────────────────────
@app.middleware("http")
async def add_timing_header(request: Request, call_next):
    start = time.perf_counter()
    response = await call_next(request)
    elapsed_ms = (time.perf_counter() - start) * 1000
    response.headers["X-Response-Time-Ms"] = f"{elapsed_ms:.1f}"
    return response


# ─── Global error handler ─────────────────────────────────────────────────────
@app.exception_handler(Exception)
async def global_exception_handler(request: Request, exc: Exception):
    logger.error("Unhandled exception for %s %s: %s", request.method, request.url, exc, exc_info=True)
    return JSONResponse(
        status_code=500,
        content={"detail": "Internal server error. Please check service logs."},
    )


# ─── Endpoints ────────────────────────────────────────────────────────────────

@app.get("/health", response_model=HealthResponse, tags=["Health"])
async def health():
    """Health check cho Spring Boot actuator và load balancer."""
    return HealthResponse()


@app.post(
    "/forecast/prophet",
    response_model=ProphetForecastResponse,
    tags=["Forecast"],
    summary="AI Demand Forecast (Prophet + Weather)",
)
async def forecast_prophet(request: ForecastRequest) -> ProphetForecastResponse:
    """
    Nhận lịch sử tiêu thụ từ Spring Boot và trả về dự báo nhu cầu:
    - Nếu đủ ≥ 30 ngày: dùng Prophet + VN Holidays + Open-Meteo Weather
    - Nếu < 30 ngày: Moving Average + Weather adjustment

    Spring Boot xử lý auth/tenant isolation; endpoint này không cần JWT.
    Chỉ accept request từ backend nội bộ (configured via ALLOWED_ORIGINS).
    """
    logger.info(
        "Forecast request: ingredient_id=%s, store_id=%s, history_points=%d, forecast_days=%d",
        request.ingredient_id,
        request.store_id,
        len(request.history),
        request.forecast_days,
    )
    try:
        result = await run_prophet_forecast(request)
        logger.info(
            "Forecast completed: ingredient_id=%s, model=%s, history_days=%d, total_demand=%.3f",
            request.ingredient_id,
            result.model_used,
            result.history_days_used,
            result.total_predicted_demand,
        )
        return result
    except Exception as exc:
        logger.error(
            "Forecast failed for ingredient_id=%s: %s",
            request.ingredient_id, exc, exc_info=True
        )
        raise HTTPException(status_code=500, detail=f"Forecast computation failed: {exc}") from exc


# ─── Dev run ──────────────────────────────────────────────────────────────────
if __name__ == "__main__":
    import uvicorn
    uvicorn.run(
        "main:app",
        host="0.0.0.0",
        port=int(os.getenv("PORT", "8001")),
        reload=os.getenv("RELOAD", "false").lower() == "true",
        workers=int(os.getenv("WORKERS", "1")),
    )
