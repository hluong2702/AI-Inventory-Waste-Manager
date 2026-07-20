import fs from "node:fs/promises";
import { FileBlob, SpreadsheetFile } from "@oai/artifact-tool";

const source = "/Users/hoangluong/Documents/AI Inventory & Waste Manager/.codex-work/template-technical/Template-Technical-sanitized.xlsx";
const outputDir = "/Users/hoangluong/Documents/AI Inventory & Waste Manager/outputs/technical-template-20260714";
const outputPath = `${outputDir}/AI-Inventory-Waste-Manager-Technical.xlsx`;
const previewDir = `${outputDir}/previews`;
await fs.mkdir(previewDir, { recursive: true });

const workbook = await SpreadsheetFile.importXlsx(await FileBlob.load(source));
const d = (y, m, day) => new Date(Date.UTC(y, m - 1, day));

// 0. Roadmap
{
  const s = workbook.worksheets.getItem("0. Roadmap");
  s.getRange("B1:AA32").clear({ applyTo: "contents" });
  s.getRange("B1").values = [["LỘ TRÌNH SẢN PHẨM AI INVENTORY & WASTE MANAGER 2026-2027"]];
  s.getRange("B2").values = [["Hạng mục / Sáng kiến"]];
  s.getRange("D2").values = [[2026]];
  s.getRange("P2").values = [[2027]];
  s.getRange("D3:AA3").values = [[1,2,3,4,5,6,7,8,9,10,11,12,1,2,3,4,5,6,7,8,9,10,11,12]];
  const rows = [
    ["", "Nền tảng & bảo mật"],
    ["1.1", "Kiến trúc multi-tenant, JWT/RBAC và Tenant Context"],
    ["1.2", "Cơ sở dữ liệu MySQL, Flyway và Redis"],
    ["", "Quản lý kho & lãng phí"],
    ["2.1", "Danh mục nguyên liệu và định mức tồn tối thiểu"],
    ["2.2", "Nhập kho theo lô, hạn sử dụng và giá vốn"],
    ["2.3", "Xuất kho FEFO và kiểm soát giao dịch đồng thời"],
    ["2.4", "Ghi nhận hao hụt và nhật ký giao dịch"],
    ["2.5", "Cảnh báo tồn thấp và sắp hết hạn"],
    ["", "Dự báo & báo cáo"],
    ["3.1", "Dashboard KPI tồn kho và lãng phí"],
    ["3.2", "Dự báo nhu cầu và khuyến nghị đặt hàng"],
    ["3.3", "Báo cáo vận hành, audit log và xuất CSV"],
    ["", "Vận hành SaaS"],
    ["4.1", "Quản lý cửa hàng và chuyển đổi multi-store"],
    ["4.2", "Mời nhân viên qua email và đổi mật khẩu lần đầu"],
    ["4.3", "Subscription, hạn mức gói và thanh toán"],
    ["4.4", "Trang quản trị hệ thống và quản lý tenant"],
    ["", "Chất lượng & triển khai"],
    ["5.1", "Kiểm thử FEFO, phân quyền và cách ly tenant"],
    ["5.2", "Tối ưu hiệu năng, giám sát và sao lưu"],
    ["5.3", "UAT, đào tạo, triển khai production và bàn giao"],
  ];
  s.getRange(`B4:C${3 + rows.length}`).values = rows;
  s.getRange("D4:AA32").format = { fill: "#FFFFFF", font: { color: "#000000" } };
  for (const row of [4,7,13,17,22]) {
    s.getRange(`B${row}:AA${row}`).format = { fill: "#D9D9D9", font: { bold: true, color: "#000000" } };
  }
  const bands = [[5,6,7,8],[6,7,8],[8,9],[8,9],[8,9],[8,9],[8,9,10],[9,10],[10,11],[8,9],[9,10],[10,11],[9],[9,10],[10],[10,11],[10,11],[11],[8,9,10],[10,11],[11,12,13]];
  let bi = 0;
  for (let r = 4; r <= 25; r++) {
    if ([4,7,13,17,22].includes(r)) continue;
    const months = bands[bi++] ?? [];
    for (const month of months) {
      const col = month + 3;
      s.getCell(r - 1, col - 1).values = [["●"]];
      s.getCell(r - 1, col - 1).format = { fill: "#70AD47", font: { color: "#FFFFFF", bold: true }, horizontalAlignment: "center" };
    }
  }
  s.getRange("B4:AA25").format.wrapText = true;
  s.getRange("B26:AA32").clear({ applyTo: "all" });
}

// 1. Project Charter
{
  const s = workbook.worksheets.getItem("1. Project Charter");
  s.getRange("B2").values = [["AI INVENTORY & WASTE MANAGER"]];
  s.getRange("B3").values = [["Doanh nghiệp F&B / Đơn vị vận hành hệ thống"]];
  s.getRange("B5").values = [["Hoàng Lương"]];
  s.getRange("B6").values = [["Hoàng Lương"]];
  s.getRange("B7").values = [["Nhóm phát triển AI Inventory & Waste Manager"]];
  s.getRange("B8").values = [[d(2026,7,14)]];
  s.getRange("E8").values = [[d(2026,10,16)]];
  s.getRange("B8").format.numberFormat = "dd/mm/yyyy";
  s.getRange("E8").format.numberFormat = "dd/mm/yyyy";
  s.getRange("B9").values = [["Xây dựng nền tảng SaaS đa cửa hàng giúp doanh nghiệp F&B quản lý tồn kho theo lô và FEFO, cảnh báo rủi ro hết hạn/tồn thấp, ghi nhận lãng phí, dự báo nhu cầu đặt hàng và kiểm soát vận hành theo vai trò."]];
  s.getRange("B10").values = [["Web application responsive; quản lý tenant/store; JWT & RBAC; nguyên liệu, lô hàng, nhập/xuất FEFO; hao hụt; cảnh báo; dự báo; báo cáo; nhân sự; gói dịch vụ và quản trị hệ thống."]];
  s.getRange("E10").values = [["Không bao gồm giao nhận vật lý, tính lương, kế toán tổng hợp, quản lý nhà cung cấp/PO đầy đủ và tích hợp POS thời gian thực trong giai đoạn hiện tại."]];
  s.getRange("B11").values = [["Theo ngân sách được phê duyệt; effort ước tính chi tiết tại sheet 2. Price."]];
  s.getRange("E11").values = [["Nhóm phát triển AI Inventory & Waste Manager"]];
  s.getRange("B13").values = [["Mã nguồn frontend React/TypeScript và backend Spring Boot; CSDL MySQL/Flyway; tài liệu SRS và thiết kế kỹ thuật; bộ kiểm thử; hướng dẫn triển khai/vận hành; hệ thống UAT đáp ứng phạm vi, bảo mật cách ly tenant và quy trình FEFO."]];
  s.getRange("B9:B13").format.wrapText = true;
}

// 2. Price / effort estimate
{
  const s = workbook.worksheets.getItem("2. Price");
  s.getRange("A1:F53").clear({ applyTo: "contents" });
  s.getRange("A1").values = [["AI INVENTORY & WASTE MANAGER"]];
  s.getRange("A4").values = [["Đơn vị: Nhóm phát triển AI Inventory & Waste Manager"]];
  s.getRange("B5").values = [["Dự án: AI Inventory & Waste Manager"]];
  s.getRange("E5").values = [["Từ ngày: 14/07/2026"]];
  s.getRange("A6").values = [["Khách hàng: Doanh nghiệp F&B / Đơn vị vận hành"]];
  s.getRange("E6").values = [["Đến ngày: 16/10/2026"]];
  s.getRange("A10:F10").values = [["STT","Nội dung công việc","Ghi chú","Thời gian (ngày)","Effort (manday)","Comment"]];
  const data = [
    ["I","PHÂN TÍCH VÀ THIẾT KẾ","",null,null,""],
    [1,"Lập kế hoạch và quản trị phạm vi","WBS, mốc, rủi ro, kế hoạch truyền thông",3,3,""],
    [2,"Phân tích nghiệp vụ F&B","Luồng nhập/xuất, FEFO, hao hụt, cảnh báo",5,8,""],
    [3,"Đặc tả yêu cầu và tiêu chí nghiệm thu","SRS, business rules, acceptance criteria",5,8,""],
    [4,"Thiết kế kiến trúc hệ thống","React SPA, Spring Boot, MySQL, Redis",4,8,""],
    [5,"Thiết kế CSDL và giao diện","Schema multi-tenant, wireframe responsive",7,14,""],
    ["II","PHÁT TRIỂN VÀ XÂY DỰNG","",null,null,""],
    [1,"Nền tảng hệ thống","Multi-tenant, bảo mật, cấu hình dùng chung",null,null,""],
    ["1.1","Đăng ký, đăng nhập và refresh token","JWT, BCrypt, phiên đăng nhập",5,8,""],
    ["1.2","Phân quyền SYSTEM_ADMIN/OWNER/MANAGER/STAFF","Route/API guards và kiểm tra quyền",4,7,""],
    ["1.3","Tenant Context và chuyển đổi cửa hàng","Cách ly dữ liệu theo x-store-id",6,10,""],
    ["1.4","Quản lý store và trạng thái hoạt động","CRUD, khóa/mở và quyền sở hữu",4,7,""],
    [2,"Quản lý kho FEFO","Nguyên liệu, lô hàng, giao dịch kho",null,null,""],
    ["2.1","Quản lý danh mục nguyên liệu","Đơn vị tính, min stock, unit cost",5,9,""],
    ["2.2","Nhập kho theo lô","Batch number, HSD, giá vốn",5,9,""],
    ["2.3","Xuất kho tự động theo FEFO","Pessimistic lock, chống tồn âm",7,14,""],
    ["2.4","Ghi nhận hao hụt","Lý do, số lượng, giá trị thất thoát",4,7,""],
    ["2.5","Lịch sử giao dịch và kiểm kê","Tra cứu, lọc, phân trang",4,7,""],
    [3,"Cảnh báo, dự báo và báo cáo","Dashboard và hỗ trợ quyết định",null,null,""],
    ["3.1","Cảnh báo tồn kho thấp","Theo min stock và lịch quét",3,5,""],
    ["3.2","Cảnh báo sắp hết hạn","Theo lô và ngưỡng ngày",3,5,""],
    ["3.3","Dự báo nhu cầu tiêu thụ","Average usage và reorder suggestion",6,10,""],
    ["3.4","Dashboard KPI","Tồn kho, hao hụt, cảnh báo, hành động",5,9,""],
    ["3.5","Báo cáo và xuất CSV","Waste, transaction, audit log",4,7,""],
    [4,"Nhân sự và thương mại hóa","Invitation, subscription, billing",null,null,""],
    ["4.1","Mời nhân viên qua email","Token, mật khẩu tạm, rate limit",5,9,""],
    ["4.2","Đổi mật khẩu lần đầu","MustChangePasswordFilter",3,5,""],
    ["4.3","Quản lý nhân sự cửa hàng","Danh sách, vai trò, trạng thái",4,7,""],
    ["4.4","Gói FREE/BASIC/PRO/ENTERPRISE","Hạn mức theo entitlement",5,9,""],
    ["4.5","Thanh toán và webhook","Kích hoạt/gia hạn subscription",5,9,""],
    ["4.6","Trang quản trị hệ thống","Tenant, user và giao dịch",6,10,""],
    [5,"Kiểm thử và chất lượng","",null,null,""],
    ["5.1","Unit/integration test backend","FEFO, auth, tenant, billing",7,12,""],
    ["5.2","Kiểm thử frontend và responsive","Các luồng chính, lỗi và trạng thái rỗng",5,9,""],
    ["5.3","Kiểm thử bảo mật và UAT","Phân quyền, rò rỉ tenant, nghiệm thu",6,10,""],
    ["III","TÍCH HỢP, TRIỂN KHAI VÀ BÀN GIAO","",null,null,""],
    ["6.1","Tích hợp Gmail SMTP và Redis","Email invitation, cache/rate limit",3,5,""],
    ["6.2","Chuẩn bị môi trường production","Java 21, MySQL 8, Redis, reverse proxy",4,7,""],
    ["6.3","Migration dữ liệu và cấu hình","Flyway, secrets, backup",3,5,""],
    ["6.4","Đào tạo, tài liệu và bàn giao","HDSD, vận hành, hỗ trợ sau go-live",5,8,""],
  ];
  s.getRange(`A11:F${10 + data.length}`).values = data;
  s.getRange("A53:C53").values = [[null,"TỔNG",""]];
  s.getRange("D53:E53").formulas = [["=SUM(D12:D52)","=SUM(E12:E52)"]];
  s.getRange("D12:E53").format.numberFormat = "#,##0";
  s.getRange("B11:C53").format.wrapText = true;
}

// 3. Master Plan
{
  const s = workbook.worksheets.getItem("3. Master Plan");
  s.getRange("A1").values = [["Dự án AI Inventory & Waste Manager"]];
  s.getRange("A2").values = [["Đơn vị: Nhóm phát triển AI Inventory & Waste Manager"]];
  s.getRange("A3").values = [["Người lập: Hoàng Lương"]];
  s.getRange("G3").values = [[d(2026,7,14)]];
  s.getRange("G3").format.numberFormat = "dd/mm/yyyy";
  s.getRange("G4").values = [["Weekly"]];
  s.getRange("G5").values = [[1]];
  s.getRange("A8:L90").clear({ applyTo: "contents" });
  s.getRange("A7:L7").values = [["STT","Công việc","Tóm tắt nội dung","Phụ trách","Phối hợp","Tiến độ","Kế hoạch Bắt đầu","Kế hoạch Kết thúc","Kế hoạch Số ngày","Thực tế Bắt đầu","Thực tế Kết thúc","Thực tế Số ngày"]];
  const tasks = [
    ["I","KHỞI TẠO VÀ PHÂN TÍCH","Phạm vi, yêu cầu và kiến trúc","PM","Các bên liên quan",1,d(2026,7,14),d(2026,7,24)],
    [1,"Khởi động dự án","Thống nhất mục tiêu, phạm vi và cách làm việc","PM","Product Owner",1,d(2026,7,14),d(2026,7,14)],
    [2,"Phân tích quy trình kho F&B","Nhập/xuất, FEFO, hao hụt và cảnh báo","BA","PM, vận hành",0.8,d(2026,7,14),d(2026,7,20)],
    [3,"Hoàn thiện SRS và backlog","Yêu cầu chức năng, phi chức năng, acceptance criteria","BA","Tech Lead",0.7,d(2026,7,17),d(2026,7,23)],
    [4,"Thiết kế kiến trúc và CSDL","Multi-tenant, API, schema, bảo mật","Tech Lead","Backend, DevOps",0.6,d(2026,7,20),d(2026,7,24)],
    ["II","NỀN TẢNG & BẢO MẬT","Auth, tenant, store và phân quyền","Tech Lead","Backend, Frontend",0.35,d(2026,7,27),d(2026,8,14)],
    [1,"Xác thực JWT và refresh token","Đăng ký, đăng nhập, đổi mật khẩu","Backend","Frontend, QA",0.6,d(2026,7,27),d(2026,8,3)],
    [2,"RBAC và route/API guards","4 vai trò hệ thống","Backend","Frontend, QA",0.45,d(2026,7,30),d(2026,8,6)],
    [3,"Tenant Context và multi-store","Cách ly dữ liệu và chuyển store","Backend","Frontend, QA",0.35,d(2026,8,3),d(2026,8,12)],
    [4,"Quản lý store","CRUD, trạng thái, quyền sở hữu","Backend","Frontend",0.25,d(2026,8,7),d(2026,8,14)],
    ["III","QUẢN LÝ KHO FEFO","Nguyên liệu, lô và giao dịch","Tech Lead","Backend, Frontend, QA",0.1,d(2026,8,10),d(2026,9,4)],
    [1,"Danh mục nguyên liệu","CRUD, đơn vị, min stock, unit cost","Full-stack","QA",0.2,d(2026,8,10),d(2026,8,18)],
    [2,"Nhập kho theo lô","Batch, HSD, số lượng, giá vốn","Full-stack","QA",0.1,d(2026,8,17),d(2026,8,24)],
    [3,"Xuất kho FEFO","Khóa đồng thời và phân bổ theo lô","Backend","QA",0,d(2026,8,20),d(2026,8,31)],
    [4,"Ghi nhận hao hụt","Lý do, số lượng và giá trị","Full-stack","QA",0,d(2026,8,25),d(2026,9,2)],
    [5,"Giao dịch và kiểm kê","Lịch sử, lọc, phân trang","Full-stack","QA",0,d(2026,8,28),d(2026,9,4)],
    ["IV","CẢNH BÁO, DỰ BÁO & BÁO CÁO","Insight và hỗ trợ quyết định","Tech Lead","Data, Full-stack",0,d(2026,9,1),d(2026,9,18)],
    [1,"Cảnh báo tồn thấp/hết hạn","Job định kỳ và action center","Backend","Frontend",0,d(2026,9,1),d(2026,9,8)],
    [2,"Dự báo và reorder suggestion","Dựa trên lịch sử tiêu hao","Backend/Data","Frontend, QA",0,d(2026,9,4),d(2026,9,14)],
    [3,"Dashboard KPI","Tồn, waste, cảnh báo và xu hướng","Frontend","Backend, QA",0,d(2026,9,8),d(2026,9,16)],
    [4,"Báo cáo và xuất CSV","Waste, transaction, audit log","Full-stack","QA",0,d(2026,9,11),d(2026,9,18)],
    ["V","NHÂN SỰ, BILLING & ADMIN","Thương mại hóa SaaS","Tech Lead","Full-stack, QA",0,d(2026,9,14),d(2026,10,2)],
    [1,"Invitation và first login","Email, token, mật khẩu tạm","Full-stack","QA",0,d(2026,9,14),d(2026,9,22)],
    [2,"Quản lý nhân sự","Vai trò, trạng thái và hạn mức","Full-stack","QA",0,d(2026,9,18),d(2026,9,25)],
    [3,"Subscription và billing","Gói, entitlement, webhook","Backend","Frontend, QA",0,d(2026,9,21),d(2026,9,30)],
    [4,"System Admin","Quản lý tenant, user và giao dịch","Full-stack","QA",0,d(2026,9,25),d(2026,10,2)],
    ["VI","KIỂM THỬ, TRIỂN KHAI & BÀN GIAO","Ổn định và nghiệm thu","PM","Toàn đội",0,d(2026,9,28),d(2026,10,16)],
    [1,"Kiểm thử tích hợp và bảo mật","FEFO, tenant isolation, RBAC","QA","Backend, Frontend",0,d(2026,9,28),d(2026,10,7)],
    [2,"UAT và xử lý lỗi","Nghiệm thu với người dùng đại diện","QA/BA","Vận hành",0,d(2026,10,5),d(2026,10,12)],
    [3,"Chuẩn bị production","Hạ tầng, secrets, backup, migration","DevOps","Backend",0,d(2026,10,7),d(2026,10,13)],
    [4,"Đào tạo và go-live","Hướng dẫn, triển khai, giám sát","PM","Toàn đội",0,d(2026,10,14),d(2026,10,15)],
    [5,"Bàn giao và đóng dự án","Mã nguồn, tài liệu, biên bản","PM","Product Owner",0,d(2026,10,16),d(2026,10,16)],
  ];
  const values = tasks.map((t) => [...t, null, null, null, null]);
  s.getRange(`A8:L${7 + values.length}`).values = values;
  for (let row = 8; row <= 7 + values.length; row++) {
    s.getRange(`I${row}`).formulas = [[`=IF(OR(ISBLANK(G${row}),ISBLANK(H${row})),"",H${row}-G${row}+1)`]];
    s.getRange(`L${row}`).formulas = [[`=IF(OR(ISBLANK(J${row}),ISBLANK(K${row})),"",K${row}-J${row}+1)`]];
  }
  s.getRange(`F8:F${7 + values.length}`).format.numberFormat = "0%";
  s.getRange(`G8:H${7 + values.length}`).format.numberFormat = "dd/mm/yyyy";
  s.getRange(`J8:K${7 + values.length}`).format.numberFormat = "dd/mm/yyyy";
  s.getRange(`B8:C${7 + values.length}`).format.wrapText = true;
  s.getRange("A40:BO1000").clear({ applyTo: "all" });
}

// 4. Personnel
{
  const s = workbook.worksheets.getItem("4. Nhân sự dự án");
  s.getRange("A3:H15").clear({ applyTo: "contents" });
  const rows = [
    ["Nhóm triển khai dự án",null,null,null,null,null,null,null],
    [1,"Hoàng Lương","Nhóm dự án","Quản trị dự án","Lập kế hoạch, điều phối, nghiệm thu",null,null,"Đầu mối dự án"],
    [2,"Chưa phân công","Nhóm dự án","Business Analyst","Phân tích nghiệp vụ, SRS, UAT",null,null,"Cần cập nhật người phụ trách"],
    [3,"Chưa phân công","Nhóm dự án","Tech Lead / Backend","Kiến trúc, API, bảo mật, FEFO",null,null,"Cần cập nhật người phụ trách"],
    [4,"Chưa phân công","Nhóm dự án","Frontend Developer","React UI, state, responsive",null,null,"Cần cập nhật người phụ trách"],
    [5,"Chưa phân công","Nhóm dự án","QA Engineer","Test plan, regression, UAT",null,null,"Cần cập nhật người phụ trách"],
    [6,"Chưa phân công","Nhóm dự án","DevOps","CI/CD, hạ tầng, backup, monitoring",null,null,"Cần cập nhật người phụ trách"],
    ["Đơn vị nghiệp vụ / Khách hàng",null,null,null,null,null,null,null],
    [1,"Chưa phân công","Đơn vị F&B","Product Owner","Phê duyệt phạm vi và nghiệm thu",null,null,"Cần cập nhật đại diện"],
    [2,"Chưa phân công","Đơn vị F&B","Quản lý cửa hàng","Cung cấp quy trình và dữ liệu UAT",null,null,"Cần cập nhật đại diện"],
    [3,"Chưa phân công","Đơn vị F&B","Nhân viên kho","Thử nghiệm nhập/xuất và hao hụt",null,null,"Cần cập nhật đại diện"],
  ];
  s.getRange(`A3:H${2 + rows.length}`).values = rows;
  s.getRange("A3:H15").format.wrapText = true;
  s.getRange("A16:Z1000").clear({ applyTo: "all" });
}

// 7. Progress update
{
  const s = workbook.worksheets.getItem("7. Cập nhật tiến độ");
  s.getRange("F2").values = [["DỰ ÁN AI INVENTORY & WASTE MANAGER"]];
  s.getRange("F5").values = [["Project Manager: Hoàng Lương\nQuản trị dự án"]];
  s.getRange("F6").values = [["Workstream Lead: Tech Lead\nTrưởng nhóm công việc"]];
  s.getRange("F7").values = [["Updated by: Hoàng Lương\nCập nhật bởi"]];
  s.getRange("F8").values = [["Updated on: 14/07/2026\nNgày cập nhật"]];
  s.getRange("A13:K30").clear({ applyTo: "contents" });
  const actions = [
    [1,d(2026,7,14),"Nội bộ","Khởi tạo","Kick-off dự án","Thống nhất phạm vi, mốc và kênh trao đổi","Completed",d(2026,7,14),null,"PM","Biên bản kick-off và charter"],
    [2,d(2026,7,14),"Nội bộ","Phân tích","Hoàn thiện backlog","Rà soát SRS, business rules và acceptance criteria","In Progress",d(2026,7,23),null,"BA","Ưu tiên luồng FEFO và tenant isolation"],
    [3,d(2026,7,14),"Nội bộ","Kỹ thuật","Chốt kiến trúc","Xác nhận React, Spring Boot, MySQL, Redis và phương án triển khai","In Progress",d(2026,7,24),null,"Tech Lead","Cần chốt secrets và môi trường production"],
    [4,d(2026,7,14),"Nội bộ","Chất lượng","Kế hoạch kiểm thử","Lập test matrix cho RBAC, multi-tenant và FEFO","Not Started",d(2026,7,31),null,"QA","Bao gồm race condition khi xuất kho"],
    [5,d(2026,7,14),"Ngoài dự án","Nghiệp vụ","Đại diện UAT","Chỉ định Product Owner, quản lý cửa hàng và nhân viên kho thử nghiệm","Not Started",d(2026,7,21),null,"Khách hàng","Cần thông tin liên hệ"],
    [6,d(2026,7,14),"Nội bộ","Hạ tầng","Java 21 và dịch vụ phụ trợ","Chuẩn bị JDK 21, MySQL 8, Redis và SMTP","Not Started",d(2026,8,3),null,"DevOps","Môi trường hiện tại cần nâng JDK"],
    [7,d(2026,7,14),"Nội bộ","Bảo mật","Kiểm tra cách ly tenant","Xây dựng test truy cập chéo store và kiểm tra TenantContext.clear()","Not Started",d(2026,10,7),null,"QA/Backend","Tiêu chí bắt buộc trước UAT"],
    [8,d(2026,7,14),"Nội bộ","Triển khai","Tài liệu vận hành","Hoàn thiện hướng dẫn cấu hình, backup, restore và xử lý sự cố","Not Started",d(2026,10,13),null,"DevOps/PM","Bàn giao cùng mã nguồn"],
  ];
  s.getRange(`A13:K${12 + actions.length}`).values = actions;
  s.getRange(`B13:B${12 + actions.length}`).format.numberFormat = "dd/mm/yyyy";
  s.getRange(`H13:I${12 + actions.length}`).format.numberFormat = "dd/mm/yyyy";
  s.getRange("K4:K9").formulas = [["=COUNTIF(G13:G200,J4)"],["=COUNTIF(G13:G200,J5)"],["=COUNTIF(G13:G200,J6)"],["=COUNTIF(G13:G200,J7)"],["=COUNTIF(G13:G200,J8)"],["=COUNTIF(G13:G200,J9)"]];
  s.getRange("K10").formulas = [["=SUM(K4:K9)"]];
  s.getRange("A13:K30").format.wrapText = true;
  s.getRange("A31:Z1000").clear({ applyTo: "all" });
}

// Refresh the compact legacy master plan as an executive workstream summary.
{
  const s = workbook.worksheets.getItem("2. Master Planxxx");
  s.getRange("A4").values = [["TRIỂN KHAI AI INVENTORY & WASTE MANAGER"]];
  s.getRange("H4").values = [[2026]];
  s.getRange("AF4").values = [[2027]];
  s.getRange("A8:G37").clear({ applyTo: "contents" });
  s.getRange("H8:AO37").format = { fill: "#FFFFFF", font: { color: "#000000" }, borders: { preset: "all", style: "thin", color: "#B7B7B7" } };
  const rows = [
    ["KHỞI TẠO DỰ ÁN","",null,null,null,null,null],
    ["KT01","Kick-off và Project Charter",1,"Done","PM",null,d(2026,7,14)],
    ["KT02","Phân tích nghiệp vụ và hoàn thiện SRS",8,"In progress","BA",null,d(2026,7,23)],
    ["KT03","Thiết kế kiến trúc, CSDL và bảo mật",8,"In progress","Tech Lead",null,d(2026,7,24)],
    ["WORKSTREAM 1","NỀN TẢNG & BẢO MẬT",null,null,null,null,null],
    ["WS01.1","Auth, refresh token và đổi mật khẩu",8,"In progress","Backend",null,d(2026,8,3)],
    ["WS01.2","RBAC, Tenant Context và multi-store",14,"Not start","Full-stack",null,d(2026,8,14)],
    ["WORKSTREAM 2","QUẢN LÝ KHO FEFO",null,null,null,null,null],
    ["WS02.1","Nguyên liệu và nhập kho theo lô",14,"Not start","Full-stack",null,d(2026,8,24)],
    ["WS02.2","Xuất kho FEFO và kiểm soát đồng thời",10,"Not start","Backend",null,d(2026,8,31)],
    ["WS02.3","Hao hụt, giao dịch và kiểm kê",10,"Not start","Full-stack",null,d(2026,9,4)],
    ["WORKSTREAM 3","CẢNH BÁO, DỰ BÁO & BÁO CÁO",null,null,null,null,null],
    ["WS03.1","Cảnh báo tồn thấp và hết hạn",6,"Not start","Backend",null,d(2026,9,8)],
    ["WS03.2","Forecast, reorder suggestion và dashboard",10,"Not start","Full-stack",null,d(2026,9,16)],
    ["WS03.3","Báo cáo, audit log và xuất CSV",6,"Not start","Full-stack",null,d(2026,9,18)],
    ["WORKSTREAM 4","NHÂN SỰ, BILLING & ADMIN",null,null,null,null,null],
    ["WS04.1","Invitation và quản lý nhân sự",9,"Not start","Full-stack",null,d(2026,9,25)],
    ["WS04.2","Subscription, billing và webhook",8,"Not start","Backend",null,d(2026,9,30)],
    ["WS04.3","System Admin",6,"Not start","Full-stack",null,d(2026,10,2)],
    ["WORKSTREAM 5","KIỂM THỬ, UAT & GO-LIVE",null,null,null,null,null],
    ["WS05.1","Integration/security test",8,"Not start","QA",null,d(2026,10,7)],
    ["WS05.2","UAT và sửa lỗi",6,"Not start","QA/BA",null,d(2026,10,12)],
    ["WS05.3","Triển khai, đào tạo và bàn giao",4,"Not start","PM/DevOps",null,d(2026,10,16)],
  ];
  s.getRange(`A8:G${7 + rows.length}`).values = rows;
  s.getRange(`G8:G${7 + rows.length}`).format.numberFormat = "dd/mm/yyyy";
  s.getRange(`B8:B${7 + rows.length}`).format.wrapText = true;
  s.getRange("A32:AO1000").clear({ applyTo: "all" });
}

const errorScan = await workbook.inspect({
  kind: "match",
  searchTerm: "#REF!|#DIV/0!|#VALUE!|#NAME\\?|#N/A",
  options: { useRegex: true, maxResults: 100 },
  summary: "final formula error scan",
});
console.log("ERROR_SCAN\n" + errorScan.ndjson);

for (const sheet of workbook.worksheets.items) {
  const preview = await workbook.render({ sheetName: sheet.name, autoCrop: "all", scale: 1, format: "png" });
  const safe = sheet.name.replace(/[^a-zA-Z0-9_-]+/g, "_");
  await fs.writeFile(`${previewDir}/${safe}.png`, new Uint8Array(await preview.arrayBuffer()));
}

await fs.mkdir(outputDir, { recursive: true });
const output = await SpreadsheetFile.exportXlsx(workbook);
await output.save(outputPath);
console.log(outputPath);
