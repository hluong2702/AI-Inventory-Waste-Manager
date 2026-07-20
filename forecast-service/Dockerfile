FROM python:3.11-slim

# Cài build tools cần thiết cho pystan (prophet dependency)
RUN apt-get update && apt-get install -y --no-install-recommends \
    gcc g++ make libgomp1 \
    && rm -rf /var/lib/apt/lists/*

WORKDIR /app

# Cài requirements trước (cache layer)
COPY requirements.txt .
RUN pip install --no-cache-dir -r requirements.txt

# Copy source code
COPY . .

# Expose port
EXPOSE 8001

# Health check
HEALTHCHECK --interval=30s --timeout=10s --start-period=60s --retries=3 \
    CMD python -c "import httpx; httpx.get('http://localhost:8001/health').raise_for_status()"

# Chạy với 2 workers (Prophet GIL-safe per request)
CMD ["uvicorn", "main:app", "--host", "0.0.0.0", "--port", "8001", "--workers", "2"]
