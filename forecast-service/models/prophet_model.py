"""
Core Prophet model với Weather regressors.
Xử lý:
  1. Gom và làm sạch lịch sử tiêu thụ
  2. Gọi Open-Meteo API lấy weather forecast
  3. Fit Prophet model với VN holidays + weather regressors
  4. Dự báo và tính weather_factor cho từng ngày
  5. Fallback về Moving Average khi dữ liệu không đủ
"""

from __future__ import annotations

import logging
from datetime import date, timedelta
from typing import Optional

import httpx
import numpy as np
import pandas as pd
from prophet import Prophet

from holidays import get_vietnam_holidays
from schemas import (
    DailyForecastPoint,
    ForecastRequest,
    HistoryPoint,
    ProphetForecastResponse,
)

logger = logging.getLogger(__name__)

# Ngưỡng tối thiểu ngày lịch sử để dùng Prophet (dưới → Moving Average)
MIN_HISTORY_DAYS_FOR_PROPHET = 30

# Open-Meteo API (miễn phí, không cần key)
OPEN_METEO_URL = "https://api.open-meteo.com/v1/forecast"


# ─── Weather ──────────────────────────────────────────────────────────────────

async def fetch_weather_forecast(
    latitude: float,
    longitude: float,
    days: int,
) -> dict[str, dict]:
    """
    Gọi Open-Meteo API và trả về dict {date_str: {temp_max, rain_mm}}.
    Trả về dict rỗng nếu API lỗi (graceful degradation).
    """
    params = {
        "latitude": latitude,
        "longitude": longitude,
        "daily": "temperature_2m_max,precipitation_sum",
        "forecast_days": min(days + 1, 16),  # Open-Meteo tối đa 16 ngày
        "timezone": "Asia/Ho_Chi_Minh",
    }
    try:
        async with httpx.AsyncClient(timeout=8.0) as client:
            resp = await client.get(OPEN_METEO_URL, params=params)
            resp.raise_for_status()
            data = resp.json()

        daily = data.get("daily", {})
        dates = daily.get("time", [])
        temps = daily.get("temperature_2m_max", [])
        rains = daily.get("precipitation_sum", [])

        return {
            d: {
                "temp_max": temps[i] if i < len(temps) else None,
                "rain_mm": rains[i] if i < len(rains) else None,
            }
            for i, d in enumerate(dates)
        }
    except Exception as exc:
        logger.warning("Open-Meteo API không khả dụng: %s. Bỏ qua weather regressors.", exc)
        return {}


def weather_to_condition(temp_max: Optional[float], rain_mm: Optional[float]) -> tuple[str, float]:
    """
    Chuyển đổi nhiệt độ + mưa sang mô tả tiếng Việt + hệ số điều chỉnh nhu cầu.
    Hệ số > 1: nhu cầu tăng so với bình thường
    Hệ số < 1: nhu cầu giảm
    """
    if temp_max is None and rain_mm is None:
        return "Không có dữ liệu", 1.0

    factor = 1.0
    parts = []

    # Điều chỉnh theo nhiệt độ
    if temp_max is not None:
        if temp_max >= 37:
            parts.append(f"Rất nóng ({temp_max:.0f}°C)")
            factor *= 1.15  # Nóng → uống nhiều đồ lạnh
        elif temp_max >= 33:
            parts.append(f"Nóng ({temp_max:.0f}°C)")
            factor *= 1.07
        elif temp_max <= 22:
            parts.append(f"Mát ({temp_max:.0f}°C)")
            factor *= 0.93  # Mát → giảm đồ lạnh, tăng đồ nóng
        else:
            parts.append(f"{temp_max:.0f}°C")

    # Điều chỉnh theo mưa
    if rain_mm is not None:
        if rain_mm >= 20:
            parts.append("Mưa lớn")
            factor *= 0.82  # Mưa lớn → khách ít ra đường
        elif rain_mm >= 5:
            parts.append("Mưa vừa")
            factor *= 0.91
        elif rain_mm >= 1:
            parts.append("Mưa nhẹ")
            factor *= 0.96
        else:
            parts.append("Nắng")

    return ", ".join(parts) if parts else "Bình thường", round(factor, 3)


# ─── Moving Average Fallback ──────────────────────────────────────────────────

def moving_average_forecast(
    history: list[HistoryPoint],
    forecast_days: int,
    weather_data: dict,
) -> ProphetForecastResponse:
    """
    Dự báo đơn giản dùng Moving Average (14 ngày gần nhất).
    Dùng khi lịch sử < MIN_HISTORY_DAYS_FOR_PROPHET.
    """
    values = [p.y for p in sorted(history, key=lambda x: x.ds)]
    window = min(14, len(values))
    avg_daily = np.mean(values[-window:]) if values else 0.0

    today = date.today()
    breakdown = []
    total = 0.0

    for i in range(forecast_days):
        forecast_date = today + timedelta(days=i + 1)
        date_str = forecast_date.isoformat()
        w = weather_data.get(date_str, {})
        condition, wf = weather_to_condition(w.get("temp_max"), w.get("rain_mm"))

        predicted = max(0.0, avg_daily * wf)
        total += predicted

        breakdown.append(DailyForecastPoint(
            date=forecast_date,
            predicted_demand=round(predicted, 3),
            lower_bound=round(predicted * 0.75, 3),
            upper_bound=round(predicted * 1.25, 3),
            weather_condition=condition,
            temperature_max=w.get("temp_max"),
            rain_mm=w.get("rain_mm"),
            weather_factor=wf,
            is_holiday=False,
            is_weekend=forecast_date.weekday() >= 5,
        ))

    return ProphetForecastResponse(
        ingredient_id=0,  # caller sẽ set
        store_id=0,
        forecast_days=forecast_days,
        total_predicted_demand=round(total, 3),
        daily_breakdown=breakdown,
        model_used="moving_average",
        history_days_used=len(history),
        model_accuracy_mape=None,
        confidence_note=(
            f"Dựa trên trung bình {window} ngày gần nhất "
            f"(chưa đủ {MIN_HISTORY_DAYS_FOR_PROPHET} ngày để dùng AI Prophet)"
        ),
    )


# ─── Prophet Model ────────────────────────────────────────────────────────────

def _prepare_training_df(
    history: list[HistoryPoint],
    weather_history: dict,
) -> pd.DataFrame:
    """Xây dựng DataFrame training cho Prophet với weather regressors."""
    rows = []
    vn_holidays_set = set(
        get_vietnam_holidays()["ds"].dt.date.tolist()
    )

    for point in history:
        d = point.ds
        d_str = d.isoformat()
        w = weather_history.get(d_str, {})

        rows.append({
            "ds": pd.Timestamp(d),
            "y": point.y,
            "temperature_max": w.get("temp_max", 32.0),  # default TP.HCM
            "rain_mm": w.get("rain_mm", 2.0),
            "is_weekend": float(d.weekday() >= 5),
            "is_holiday": float(d in vn_holidays_set),
        })

    return pd.DataFrame(rows).sort_values("ds").reset_index(drop=True)


def _prepare_future_df(
    forecast_days: int,
    weather_forecast: dict,
) -> pd.DataFrame:
    """Xây dựng DataFrame future cho Prophet predictions."""
    today = date.today()
    vn_holidays_set = set(
        get_vietnam_holidays()["ds"].dt.date.tolist()
    )
    rows = []

    for i in range(forecast_days):
        future_date = today + timedelta(days=i + 1)
        d_str = future_date.isoformat()
        w = weather_forecast.get(d_str, {})

        rows.append({
            "ds": pd.Timestamp(future_date),
            "temperature_max": w.get("temp_max", 32.0),
            "rain_mm": w.get("rain_mm", 2.0),
            "is_weekend": float(future_date.weekday() >= 5),
            "is_holiday": float(future_date in vn_holidays_set),
        })

    return pd.DataFrame(rows)


def _calculate_mape(actual: list[float], predicted: list[float]) -> Optional[float]:
    """Tính Mean Absolute Percentage Error (MAPE)."""
    errors = []
    for a, p in zip(actual, predicted):
        if a > 0:
            errors.append(abs((a - p) / a) * 100)
    return round(float(np.mean(errors)), 1) if errors else None


async def run_prophet_forecast(request: ForecastRequest) -> ProphetForecastResponse:
    """
    Hàm chính: fit Prophet model và dự báo.
    Trả về fallback Moving Average nếu lịch sử không đủ.
    """
    history = request.history
    forecast_days = request.forecast_days

    # Fetch weather cho cả quá khứ lẫn tương lai từ Open-Meteo
    # Open-Meteo cũng cung cấp historical data qua endpoint khác
    # Ở đây đơn giản chỉ fetch forecast vì lịch sử thường đã có sẵn từ DB
    weather_forecast = await fetch_weather_forecast(
        request.latitude, request.longitude, forecast_days
    )

    # ── Kiểm tra đủ dữ liệu chưa ─────────────────────────────────────────────
    unique_days = len(set(p.ds for p in history))
    if unique_days < MIN_HISTORY_DAYS_FOR_PROPHET:
        result = moving_average_forecast(history, forecast_days, weather_forecast)
        result.ingredient_id = request.ingredient_id
        result.store_id = request.store_id
        return result

    # ── Fit Prophet ──────────────────────────────────────────────────────────
    try:
        train_df = _prepare_training_df(history, {})  # weather history không có sẵn, dùng default
        vn_holidays = get_vietnam_holidays()

        model = Prophet(
            holidays=vn_holidays,
            yearly_seasonality=True,
            weekly_seasonality=True,
            daily_seasonality=False,
            seasonality_mode="multiplicative",
            interval_width=0.80,  # 80% confidence interval
            changepoint_prior_scale=0.05,  # conservative — ít overfit
        )

        # Thêm các regressor thời tiết
        model.add_regressor("temperature_max", standardize=True)
        model.add_regressor("rain_mm", standardize=True)
        model.add_regressor("is_weekend", standardize=False)
        model.add_regressor("is_holiday", standardize=False)

        model.fit(train_df)

        # ── Cross-validation để tính MAPE (chỉ khi > 60 ngày) ──────────────
        mape: Optional[float] = None
        if unique_days >= 60:
            try:
                from prophet.diagnostics import cross_validation, performance_metrics
                cv_horizon = f"{min(7, unique_days // 5)} days"
                cv_initial = f"{max(30, unique_days - 20)} days"
                df_cv = cross_validation(
                    model,
                    initial=cv_initial,
                    horizon=cv_horizon,
                    parallel=None,
                )
                df_perf = performance_metrics(df_cv, rolling_window=1)
                mape = round(float(df_perf["mape"].iloc[-1] * 100), 1)
            except Exception as cv_err:
                logger.debug("Cross-validation skipped: %s", cv_err)

        # ── Predict future ───────────────────────────────────────────────────
        future_df = _prepare_future_df(forecast_days, weather_forecast)
        forecast_df = model.predict(future_df)

        vn_holidays_set = set(vn_holidays["ds"].dt.date.tolist())
        breakdown = []
        total = 0.0

        for _, row in forecast_df.iterrows():
            forecast_date = row["ds"].date()
            d_str = forecast_date.isoformat()
            w = weather_forecast.get(d_str, {})

            # Tính weather_factor riêng để hiển thị trên UI
            _, wf = weather_to_condition(w.get("temp_max"), w.get("rain_mm"))
            condition, _ = weather_to_condition(w.get("temp_max"), w.get("rain_mm"))

            predicted = max(0.0, float(row["yhat"]))
            lower = max(0.0, float(row["yhat_lower"]))
            upper = max(0.0, float(row["yhat_upper"]))
            total += predicted

            breakdown.append(DailyForecastPoint(
                date=forecast_date,
                predicted_demand=round(predicted, 3),
                lower_bound=round(lower, 3),
                upper_bound=round(upper, 3),
                weather_condition=condition,
                temperature_max=w.get("temp_max"),
                rain_mm=w.get("rain_mm"),
                weather_factor=wf,
                is_holiday=forecast_date in vn_holidays_set,
                is_weekend=forecast_date.weekday() >= 5,
            ))

        accuracy_note = ""
        if mape is not None:
            accuracy_note = f" | Độ chính xác back-test: {mape:.1f}% MAPE"

        return ProphetForecastResponse(
            ingredient_id=request.ingredient_id,
            store_id=request.store_id,
            forecast_days=forecast_days,
            total_predicted_demand=round(total, 3),
            daily_breakdown=breakdown,
            model_used="prophet",
            history_days_used=unique_days,
            model_accuracy_mape=mape,
            confidence_note=(
                f"AI Prophet với {unique_days} ngày lịch sử + thời tiết dự báo {forecast_days} ngày"
                f"{accuracy_note}"
            ),
        )

    except Exception as exc:
        logger.error(
            "Prophet fit/predict failed for ingredient_id=%s, store_id=%s: %s",
            request.ingredient_id, request.store_id, exc,
            exc_info=True,
        )
        # Fallback về Moving Average
        result = moving_average_forecast(history, forecast_days, weather_forecast)
        result.ingredient_id = request.ingredient_id
        result.store_id = request.store_id
        return result
