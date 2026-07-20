import fs from "node:fs/promises";
import path from "node:path";
import { FileBlob, SpreadsheetFile } from "@oai/artifact-tool";

const finalPath = "/Users/hoangluong/Documents/AI Inventory & Waste Manager/outputs/019f64c3/AI-Inventory-Waste-Manager-Technical_07-07_to_18-07-2026.xlsx";
const inputPath = process.argv[2] === "verify"
  ? finalPath
  : "/Users/hoangluong/Documents/AI Inventory & Waste Manager/AI-Inventory-Waste-Manager-Technical.xlsx";
const workDir = "/Users/hoangluong/Documents/AI Inventory & Waste Manager/.codex-spreadsheet-work/019f64c3";

const input = await FileBlob.load(inputPath);
const workbook = await SpreadsheetFile.importXlsx(input);

if (process.argv[2] === "verify") {
  const checks = await workbook.inspect({
    kind: "table",
    range: "3. Master Plan!F3:I39",
    include: "values,formulas",
    tableMaxRows: 50,
    tableMaxCols: 8,
    maxChars: 12000,
  });
  console.log(checks.ndjson);
  const errors = await workbook.inspect({
    kind: "match",
    searchTerm: "#REF!|#DIV/0!|#VALUE!|#NAME\\?|#N/A",
    options: { useRegex: true, maxResults: 300 },
    summary: "exported workbook formula error scan",
  });
  console.log(errors.ndjson);
  process.exit(0);
}

if (process.argv[2] === "edit") {
  const oldStart = 46217; // 2026-07-14
  const oldEnd = 46311; // 2026-10-16
  const newStart = 46210; // 2026-07-07
  const newEnd = 46221; // 2026-07-18
  const outputDir = "/Users/hoangluong/Documents/AI Inventory & Waste Manager/outputs/019f64c3";
  const outputPath = finalPath;

  const rescaleDate = (value) => {
    if (typeof value !== "number") return value;
    const ratio = (value - oldStart) / (oldEnd - oldStart);
    return Math.max(newStart, Math.min(newEnd, newStart + Math.round(ratio * (newEnd - newStart))));
  };

  // Project Charter
  const charter = workbook.worksheets.getItem("1. Project Charter");
  charter.getRange("B8").values = [[newStart]];
  charter.getRange("E8").values = [[newEnd]];

  // Price cover dates
  const price = workbook.worksheets.getItem("2. Price");
  price.getRange("E5").values = [["Từ ngày: 07/07/2026"]];
  price.getRange("E6").values = [["Đến ngày: 18/07/2026"]];

  // Compact the detailed master plan while preserving relative sequence/overlap.
  const master = workbook.worksheets.getItem("3. Master Plan");
  master.getRange("G3").values = [[newStart]];
  const planned = master.getRange("G8:H39").values;
  const scaledPlanned = planned.map(([start, end]) => {
    if (typeof start !== "number" || typeof end !== "number") return [start, end];
    const scaledStart = rescaleDate(start);
    const scaledEnd = Math.max(scaledStart, rescaleDate(end));
    return [scaledStart, scaledEnd];
  });
  master.getRange("G8:H39").values = scaledPlanned;
  master.getRange("I8").formulas = [["=IF(OR(ISBLANK(G8),ISBLANK(H8)),\"\",H8-G8+1)"]];
  master.getRange("I8:I39").fillDown();

  // Deadline-only master plan.
  const masterLegacy = workbook.worksheets.getItem("2. Master Planxxx");
  const deadlines = masterLegacy.getRange("G9:G30").values;
  masterLegacy.getRange("G9:G30").values = deadlines.map(([value]) => [rescaleDate(value)]);

  // Action log: raise all planned entries at project start and compress due dates.
  const tracking = workbook.worksheets.getItem("7. Cập nhật tiến độ");
  tracking.getRange("F8").values = [["Updated on: 07/07/2026\nNgày cập nhật"]];
  tracking.getRange("B13:B20").values = Array.from({ length: 8 }, () => [newStart]);
  const dueDates = tracking.getRange("H13:H20").values;
  tracking.getRange("H13:H20").values = dueDates.map(([value]) => [rescaleDate(value)]);

  // The product roadmap now shows every scoped initiative inside July 2026.
  const roadmap = workbook.worksheets.getItem("0. Roadmap");
  const initiativeRows = [5, 6, 8, 9, 10, 11, 12, 14, 15, 16, 18, 19, 20, 21, 23, 24, 25];
  for (const row of initiativeRows) {
    roadmap.getRange(`D${row}:O${row}`).clear({ applyTo: "contents" });
    roadmap.getRange(`D${row}:O${row}`).format.fill = "#FFFFFF";
    roadmap.getRange(`J${row}`).values = [["●"]];
    roadmap.getRange(`J${row}`).format.fill = "#70AD47";
    roadmap.getRange(`J${row}`).format.font = { color: "#FFFFFF", bold: true };
  }

  await fs.mkdir(outputDir, { recursive: true });

  const keyChecks = [
    ["1. Project Charter", "A8:E8"],
    ["2. Price", "E5:E6"],
    ["2. Master Planxxx", "A6:G30"],
    ["3. Master Plan", "F3:I39"],
    ["7. Cập nhật tiến độ", "A8:K20"],
  ];
  for (const [sheetName, range] of keyChecks) {
    const check = await workbook.inspect({
      kind: "table",
      range: `${sheetName}!${range}`,
      include: "values,formulas",
      tableMaxRows: 50,
      tableMaxCols: 12,
      maxChars: 12000,
    });
    console.log(`CHECK ${sheetName}!${range}`);
    console.log(check.ndjson);
  }

  const errors = await workbook.inspect({
    kind: "match",
    searchTerm: "#REF!|#DIV/0!|#VALUE!|#NAME\\?|#N/A",
    options: { useRegex: true, maxResults: 300 },
    summary: "final formula error scan",
  });
  console.log("ERROR_SCAN");
  console.log(errors.ndjson);

  const previewDir = path.join(workDir, "previews-after");
  await fs.mkdir(previewDir, { recursive: true });
  for (const sheet of workbook.worksheets.items) {
    const preview = await workbook.render({ sheetName: sheet.name, autoCrop: "all", scale: 1, format: "png" });
    const safeName = sheet.name.replace(/[^a-zA-Z0-9_-]+/g, "_");
    await fs.writeFile(path.join(previewDir, `${safeName}.png`), new Uint8Array(await preview.arrayBuffer()));
  }

  const output = await SpreadsheetFile.exportXlsx(workbook);
  await output.save(outputPath);
  console.log(`OUTPUT ${outputPath}`);
  process.exit(0);
}

if (process.argv[2] === "details") {
  const targets = [
    ["1. Project Charter", "A8:E8"],
    ["2. Master Planxxx", "G4:AO30"],
    ["2. Price", "E5:F6"],
    ["3. Master Plan", "F3:M39"],
    ["3. Master Plan", "M5:CB7"],
    ["7. Cập nhật tiến độ", "F2:K20"],
  ];
  for (const [sheetName, range] of targets) {
    const sheet = workbook.worksheets.getItem(sheetName);
    const area = sheet.getRange(range);
    console.log(`TARGET ${sheetName}!${range}`);
    console.log(JSON.stringify({ values: area.values, formulas: area.formulas }));
    const formulas = await workbook.inspect({
      kind: "formula",
      sheetId: sheetName,
      range,
      maxChars: 20000,
      options: { maxResults: 500 },
    });
    console.log(formulas.ndjson);
  }
  process.exit(0);
}

const summary = await workbook.inspect({
  kind: "workbook,sheet,table,definedName,drawing",
  maxChars: 12000,
  tableMaxRows: 8,
  tableMaxCols: 12,
  tableMaxCellChars: 120,
});
console.log("SUMMARY");
console.log(summary.ndjson);

const sheetInfo = await workbook.inspect({ kind: "sheet", include: "id,name", maxChars: 8000 });
console.log("SHEETS");
console.log(sheetInfo.ndjson);

await fs.mkdir(path.join(workDir, "previews-before"), { recursive: true });
for (const sheet of workbook.worksheets.items) {
  const used = sheet.getUsedRange();
  console.log(`USED ${sheet.name}: ${used?.address ?? "none"}`);
  if (used) {
    const region = await workbook.inspect({
      kind: "region",
      sheetId: sheet.name,
      range: used.address,
      maxChars: 10000,
      tableMaxRows: 80,
      tableMaxCols: 30,
      tableMaxCellChars: 120,
    });
    console.log(`REGION ${sheet.name}`);
    console.log(region.ndjson);
  }
  const preview = await workbook.render({ sheetName: sheet.name, autoCrop: "all", scale: 1, format: "png" });
  const safeName = sheet.name.replace(/[^a-zA-Z0-9_-]+/g, "_");
  await fs.writeFile(path.join(workDir, "previews-before", `${safeName}.png`), new Uint8Array(await preview.arrayBuffer()));
}
