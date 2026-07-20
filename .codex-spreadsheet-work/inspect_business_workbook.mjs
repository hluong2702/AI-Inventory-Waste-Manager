import { FileBlob, SpreadsheetFile } from "@oai/artifact-tool";

const input = await FileBlob.load("../AI-Inventory-Waste-Manager-Technical.xlsx");
const workbook = await SpreadsheetFile.importXlsx(input);

const sheets = await workbook.inspect({
  kind: "sheet",
  include: "id,name",
  maxChars: 12000,
});
console.log("SHEETS");
console.log(sheets.ndjson);

const summary = await workbook.inspect({
  kind: "workbook,table",
  maxChars: 30000,
  tableMaxRows: 20,
  tableMaxCols: 16,
  tableMaxCellChars: 160,
});
console.log("SUMMARY");
console.log(summary.ndjson);

for (const sheet of workbook.worksheets.items) {
  const region = await workbook.inspect({
    kind: "region",
    sheetId: sheet.name,
    range: "A1:Z50",
    maxChars: 16000,
  });
  console.log(`REGION ${sheet.name}`);
  console.log(region.ndjson);
}
