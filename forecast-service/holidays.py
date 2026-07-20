"""
Lịch ngày lễ Việt Nam cho Prophet custom seasonality.
Bao gồm: Tết Nguyên Đán, các ngày lễ cố định, ngày Quốc khánh v.v.
Dữ liệu được biên soạn cho giai đoạn 2023–2027.
"""

import pandas as pd

# ─── Ngày lễ cố định (dương lịch) ─────────────────────────────────────────────
FIXED_HOLIDAYS = [
    # Tết Dương lịch
    "2024-01-01", "2025-01-01", "2026-01-01", "2027-01-01",
    # Ngày Giải phóng & Quốc tế lao động (30/4, 1/5)
    "2024-04-30", "2024-05-01",
    "2025-04-30", "2025-05-01",
    "2026-04-30", "2026-05-01",
    "2027-04-30", "2027-05-01",
    # Quốc khánh 2/9
    "2024-09-02", "2025-09-02", "2026-09-02", "2027-09-02",
    # Giỗ Tổ Hùng Vương (10/3 âm lịch) — convert sang dương lịch
    "2024-04-18",  # 10/3 âm lịch 2024
    "2025-04-07",  # 10/3 âm lịch 2025
    "2026-03-28",  # 10/3 âm lịch 2026
]

# ─── Tết Nguyên Đán (âm lịch → dương lịch, ±4 ngày trước và sau) ──────────────
TET_WINDOWS = [
    # 2024: Tết Giáp Thìn (10/2/2024 = 1 Tết)
    *pd.date_range("2024-02-07", "2024-02-15").strftime("%Y-%m-%d").tolist(),
    # 2025: Tết Ất Tỵ (29/1/2025 = 1 Tết)
    *pd.date_range("2025-01-26", "2025-02-03").strftime("%Y-%m-%d").tolist(),
    # 2026: Tết Bính Ngọ (17/2/2026 = 1 Tết)
    *pd.date_range("2026-02-14", "2026-02-22").strftime("%Y-%m-%d").tolist(),
    # 2027: Tết Đinh Mùi (6/2/2027 = 1 Tết)
    *pd.date_range("2027-02-03", "2027-02-11").strftime("%Y-%m-%d").tolist(),
]

# ─── Tổng hợp ─────────────────────────────────────────────────────────────────

def get_vietnam_holidays() -> pd.DataFrame:
    """
    Trả về DataFrame theo định dạng Prophet holidays:
      columns: ['ds', 'holiday', 'lower_window', 'upper_window']
    """
    dates = list(set(FIXED_HOLIDAYS + TET_WINDOWS))
    tet_dates = set(TET_WINDOWS)
    fixed_dates = set(FIXED_HOLIDAYS)

    rows = []
    for d in dates:
        is_tet = d in tet_dates
        rows.append({
            "ds": pd.Timestamp(d),
            "holiday": "Tết Nguyên Đán" if is_tet else "Ngày lễ Việt Nam",
            "lower_window": 0,
            "upper_window": 0,
        })

    df = pd.DataFrame(rows)
    df = df.drop_duplicates(subset=["ds"]).sort_values("ds").reset_index(drop=True)
    return df
