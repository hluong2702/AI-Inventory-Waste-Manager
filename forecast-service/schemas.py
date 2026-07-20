"""
Pydantic schemas cho Forecast Microservice.
Định nghĩa input/output contract giữa Spring Boot và Python service.
"""

from __future__ import annotations

from datetime import date
from typing import Optional

from pydantic import BaseModel, Field, field_validator


# ─── Request ──────────────────────────────────────────────────────────────────

class HistoryPoint(BaseModel):
    """Một ngày trong lịch sử tiêu thụ."""
    ds: date = Field(..., description="Ngày (ISO 8601: YYYY-MM-DD)")
    y: float = Field(..., ge=0, description="Lượng tiêu thụ (>= 0)")


class ForecastRequest(BaseModel):
    """
    Request từ Spring Boot gửi lên Python service.
    Spring Boot chịu trách nhiệm gom dữ liệu lịch sử từ DB.
    """
    ingredient_id: int = Field(..., description="ID nguyên liệu")
    store_id: int = Field(..., description="ID cửa hàng (tenant)")
    history: list[HistoryPoint] = Field(
        ...,
        min_length=1,
        description="Lịch sử tiêu thụ theo ngày (tối thiểu 1 điểm, khuyến nghị 30+)"
    )
    forecast_days: int = Field(default=7, ge=1, le=30, description="Số ngày cần dự báo")
    latitude: float = Field(
        default=10.823099,
        ge=-90, le=90,
        description="Vĩ độ cửa hàng (mặc định TP.HCM)"
    )
    longitude: float = Field(
        default=106.629662,
        ge=-180, le=180,
        description="Kinh độ cửa hàng (mặc định TP.HCM)"
    )

    @field_validator("history")
    @classmethod
    def history_must_have_positive_dates(cls, v: list[HistoryPoint]) -> list[HistoryPoint]:
        dates = [p.ds for p in v]
        if len(dates) != len(set(dates)):
            raise ValueError("history không được có ngày trùng lặp")
        return v


# ─── Response ─────────────────────────────────────────────────────────────────

class DailyForecastPoint(BaseModel):
    """Dự báo cho một ngày cụ thể."""
    date: date
    predicted_demand: float = Field(..., description="Nhu cầu dự báo (đã tích hợp weather)")
    lower_bound: float = Field(..., description="Giới hạn dưới độ tin cậy 80%")
    upper_bound: float = Field(..., description="Giới hạn trên độ tin cậy 80%")
    weather_condition: str = Field(..., description="Mô tả thời tiết tiếng Việt")
    temperature_max: Optional[float] = Field(None, description="Nhiệt độ tối đa (°C)")
    rain_mm: Optional[float] = Field(None, description="Lượng mưa dự báo (mm)")
    weather_factor: float = Field(
        default=1.0,
        description="Hệ số điều chỉnh nhu cầu do thời tiết (1.0 = bình thường)"
    )
    is_holiday: bool = Field(default=False, description="Ngày lễ Việt Nam")
    is_weekend: bool = Field(default=False, description="Cuối tuần")


class ProphetForecastResponse(BaseModel):
    """Response đầy đủ trả về Spring Boot."""
    ingredient_id: int
    store_id: int
    forecast_days: int
    total_predicted_demand: float = Field(..., description="Tổng nhu cầu dự báo trong kỳ")
    daily_breakdown: list[DailyForecastPoint]
    model_used: str = Field(
        default="prophet",
        description="'prophet' nếu đủ data, 'moving_average' nếu fallback"
    )
    history_days_used: int = Field(..., description="Số ngày lịch sử thực sự được dùng")
    model_accuracy_mape: Optional[float] = Field(
        None,
        description="Mean Absolute Percentage Error (%) — None nếu không đủ data để cross-validate"
    )
    confidence_note: str = Field(..., description="Ghi chú về độ tin cậy của dự báo")


class HealthResponse(BaseModel):
    status: str = "ok"
    model: str = "prophet-1.1"
