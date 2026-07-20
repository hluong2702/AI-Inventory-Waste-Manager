import { readFile } from 'node:fs/promises'
import { resolve } from 'node:path'

const root = resolve(import.meta.dirname, '..')

const records = [
  {
    javaFile: 'backend/src/main/java/vn/inventoryai/auth/dto/AuthResponse.java',
    javaType: 'AuthResponse',
    tsFile: 'src/services/authService.ts',
    tsType: 'BackendAuthResponse',
  },
  {
    javaFile: 'backend/src/main/java/vn/inventoryai/inventory/dto/InventoryBatchResponse.java',
    javaType: 'InventoryBatchResponse',
    tsFile: 'src/types/index.ts',
    tsType: 'InventoryBatch',
  },
  {
    javaFile: 'backend/src/main/java/vn/inventoryai/alert/dto/AlertResponse.java',
    javaType: 'AlertResponse',
    tsFile: 'src/types/index.ts',
    tsType: 'Alert',
  },
  {
    javaFile: 'backend/src/main/java/vn/inventoryai/forecast/dto/ForecastResponse.java',
    javaType: 'ForecastResponse',
    tsFile: 'src/types/index.ts',
    tsType: 'Forecast',
  },
]

const enums = [
  {
    javaFile: 'backend/src/main/java/vn/inventoryai/common/enums/Role.java',
    javaType: 'Role',
    tsFile: 'src/types/index.ts',
    tsType: 'BackendRole',
  },
  {
    javaFile: 'backend/src/main/java/vn/inventoryai/common/enums/AlertType.java',
    javaType: 'AlertType',
    tsFile: 'src/types/index.ts',
    tsType: 'AlertType',
  },
]

function sorted(values) {
  return [...values].sort()
}

function assertSame(label, backendValues, frontendValues) {
  const backend = sorted(backendValues)
  const frontend = sorted(frontendValues)
  if (JSON.stringify(backend) !== JSON.stringify(frontend)) {
    throw new Error(
      `${label} lệch contract:\n  backend: ${backend.join(', ')}\n  frontend: ${frontend.join(', ')}`,
    )
  }
}

function javaRecordFields(source, type) {
  const match = source.match(new RegExp(`public\\s+record\\s+${type}\\s*\\(([\\s\\S]*?)\\)\\s*\\{`))
  if (!match) throw new Error(`Không tìm thấy Java record ${type}`)
  return match[1]
    .split(',')
    .map((component) => component.trim().match(/([A-Za-z_$][\w$]*)$/)?.[1])
    .filter(Boolean)
}

function tsInterfaceFields(source, type) {
  const match = source.match(new RegExp(`(?:export\\s+)?interface\\s+${type}\\s*\\{([\\s\\S]*?)\\n\\}`))
  if (!match) throw new Error(`Không tìm thấy TypeScript interface ${type}`)
  return match[1]
    .split('\n')
    .map((line) => line.replace(/\/\/.*$/, '').trim().match(/^([A-Za-z_$][\w$]*)\??:/)?.[1])
    .filter(Boolean)
}

function javaEnumValues(source, type) {
  const match = source.match(new RegExp(`enum\\s+${type}\\s*\\{([\\s\\S]*?)\\}`))
  if (!match) throw new Error(`Không tìm thấy Java enum ${type}`)
  return match[1]
    .split(',')
    .map((value) => value.trim().match(/^([A-Z][A-Z0-9_]*)/)?.[1])
    .filter(Boolean)
}

function tsUnionValues(source, type) {
  const match = source.match(new RegExp(`export\\s+type\\s+${type}\\s*=([^\\n]+)`))
  if (!match) throw new Error(`Không tìm thấy TypeScript union ${type}`)
  return [...match[1].matchAll(/'([^']+)'/g)].map((value) => value[1])
}

for (const contract of records) {
  const [javaSource, tsSource] = await Promise.all([
    readFile(resolve(root, contract.javaFile), 'utf8'),
    readFile(resolve(root, contract.tsFile), 'utf8'),
  ])
  assertSame(
    `${contract.javaType} ↔ ${contract.tsType}`,
    javaRecordFields(javaSource, contract.javaType),
    tsInterfaceFields(tsSource, contract.tsType),
  )
}

for (const contract of enums) {
  const [javaSource, tsSource] = await Promise.all([
    readFile(resolve(root, contract.javaFile), 'utf8'),
    readFile(resolve(root, contract.tsFile), 'utf8'),
  ])
  assertSame(
    `${contract.javaType} ↔ ${contract.tsType}`,
    javaEnumValues(javaSource, contract.javaType),
    tsUnionValues(tsSource, contract.tsType),
  )
}

console.log(`API contract OK: ${records.length} DTOs, ${enums.length} enums`) // eslint-disable-line no-console
