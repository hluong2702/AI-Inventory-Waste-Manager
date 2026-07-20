import fs from "node:fs/promises";
import { FileBlob, SpreadsheetFile } from "@oai/artifact-tool";

const inputPath = "/Users/hoangluong/Documents/AI Inventory & Waste Manager/.codex-work/template-technical/Template-Technical-sanitized.xlsx";
const outDir = "/Users/hoangluong/Documents/AI Inventory & Waste Manager/.codex-work/template-technical/previews";
await fs.mkdir(outDir, { recursive: true });
let workbook;
try {
  workbook = await SpreadsheetFile.importXlsx(await FileBlob.load(inputPath));
} catch (error) {
  console.error("IMPORT_ERROR", error?.message, error?.stack?.split("\n").slice(-8).join("\n"));
  process.exit(1);
}

const overview = await workbook.inspect({
  kind: "workbook,sheet,table,definedName,drawing",
  maxChars: 20000,
  tableMaxRows: 10,
  tableMaxCols: 12,
  tableMaxCellChars: 120,
});
console.log("OVERVIEW\n" + overview.ndjson);

const sheets = workbook.worksheets.items;
for (let i = 0; i < sheets.length; i++) {
  const sheet = sheets[i];
  const used = sheet.getUsedRange();
  console.log(`SHEET ${i + 1}: ${sheet.name} USED ${used?.address ?? "(none)"}`);
  if (used) {
    const region = await workbook.inspect({
      kind: "region,formula,computedStyle",
      sheetId: sheet.name,
      range: used.address,
      maxChars: 12000,
      tableMaxRows: 40,
      tableMaxCols: 20,
      tableMaxCellChars: 160,
      options: { maxResults: 200 },
    });
    console.log(region.ndjson);
  }
  const preview = await workbook.render({ sheetName: sheet.name, autoCrop: "all", scale: 1.3, format: "png" });
  await fs.writeFile(`${outDir}/${String(i + 1).padStart(2, "0")}-${sheet.name.replace(/[^a-zA-Z0-9_-]+/g, "_")}.png`, new Uint8Array(await preview.arrayBuffer()));
}
