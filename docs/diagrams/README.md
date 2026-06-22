# 系統設計圖（PlantUML）

面試快速理解專案用的中文圖。所有檔案為 PlantUML 原始碼（`.puml`）。

| 檔案 | 圖種 | 看什麼 |
|---|---|---|
| `01-architecture.puml` | 元件 / 分層架構圖 | 整體分層、各元件與外部系統（MySQL/Redis/MQ）的關係、依賴分級 |
| `02-class-diagram.puml` | 類別圖 | 每個類別的職責、方法、層與層之間的依賴 |
| `03-er-diagram.puml` | 實體關聯圖 | `notification` 資料表結構與索引 |
| `04-seq-create.puml` | 序列圖 | 建立通知：寫 DB → 快取 → 發 MQ，含驗證與失敗處理 |
| `05-seq-get.puml` | 序列圖 | 依 ID 查詢的旁路快取（命中 / 未命中 / 404） |
| `06-seq-update.puml` | 序列圖 | 更新：刷新 by-id、讓最近清單失效 |
| `07-seq-delete.puml` | 序列圖 | 刪除：移除 by-id、讓最近清單失效 |
| `08-seq-recent.puml` | 序列圖 | 最近清單：Redis 優先、未命中由 DB 重建 |
| `09-activity-create.puml` | 活動流程圖 | 建立通知的決策流程（硬性 vs 盡力步驟） |
| `10-deployment.puml` | 部署圖 | docker-compose 的容器拓撲與連線 |

---

## 怎麼開啟、直接看到圖

**最快（不裝任何東西）**
1. **PlantUML 線上版** — 開 https://www.plantuml.com/plantuml/uml/ ，把 `.puml` 內容貼進去，右邊即時出圖。
2. **VS Code 外掛** — 安裝「**PlantUML**」(jebbs) 外掛，打開 `.puml` 按 `Alt+D` 即時預覽。
   （此外掛需要本機有 Java，或在設定改用線上 render server。）
3. **JetBrains IDE（IntelliJ）** — 安裝「PlantUML Integration」外掛，打開 `.puml` 直接看預覽。

**匯出成 PNG / SVG（本機，需要 Java）**
```bash
# 用 Docker 一次把所有圖轉成 PNG（不需本機裝 plantuml）
docker run --rm -v "$PWD/docs/diagrams":/data plantuml/plantuml -tpng "/data/*.puml"
# 或轉 SVG（向量、放大不糊）
docker run --rm -v "$PWD/docs/diagrams":/data plantuml/plantuml -tsvg "/data/*.puml"
```
產生的 `.png` / `.svg` 就能用任何看圖軟體或瀏覽器直接開啟。

**Obsidian / Typora 等 Markdown 編輯器**
- 安裝 PlantUML 外掛後，可在 markdown 內用 ```` ```plantuml ```` 區塊直接渲染。
