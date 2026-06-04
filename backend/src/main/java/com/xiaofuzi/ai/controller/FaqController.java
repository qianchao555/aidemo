package com.xiaofuzi.ai.controller;

import com.xiaofuzi.ai.annotation.RequireRole;
import com.xiaofuzi.ai.dto.PageResult;
import com.xiaofuzi.ai.entity.FaqEntry;
import com.xiaofuzi.ai.service.FaqService;
import com.xiaofuzi.ai.vo.Result;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;

@RestController
@RequestMapping("/faq")
public class FaqController {
    private static final Logger logger = LoggerFactory.getLogger(FaqController.class);
    private final FaqService faqService;

    private static final Set<String> ALLOWED_SORT_COLUMNS = Set.of(
        "id", "question", "category", "hitCount", "status", "createTime", "updateTime", "lastHitTime"
    );

    private static final Map<String, String> SORT_COLUMN_MAPPING = Map.of(
        "question", "question",
        "category", "category",
        "hitCount", "hit_count",
        "status", "status",
        "createTime", "create_time",
        "updateTime", "update_time",
        "lastHitTime", "last_hit_time"
    );

    public FaqController(FaqService faqService) {
        this.faqService = faqService;
    }


    /***********************************FAQ 管理 API*******************************/

    @RequireRole("admin")
    @PostMapping("/create-faq")
    public Result<FaqEntry> createFaq(@RequestBody FaqEntry faqEntry) {
        faqService.create(faqEntry);
        return Result.success(faqEntry);
    }

    @RequireRole("admin")
    @PutMapping("/faq/{id}")
    public Result<FaqEntry> updateFaq(@PathVariable Long id, @RequestBody FaqEntry faqEntry) {
        faqEntry.setId(id);
        faqService.update(faqEntry);
        return Result.success(faqEntry);
    }

    @RequireRole("admin")
    @DeleteMapping("/faq/{id}")
    public Result<Map<String, Object>> deleteFaq(@PathVariable Long id) {
        faqService.delete(id);
        return Result.success(Map.of("success", true, "message", "FAQ 已删除"));
    }

    @GetMapping("/faq/{id}")
    public Result<FaqEntry> getFaq(@PathVariable Long id) {
        FaqEntry entry = faqService.findById(id);
        return entry != null ? Result.success(entry) : Result.error("FAQ 不存在");
    }

    @GetMapping("/faq")
    public Result<PageResult<FaqEntry>> listFaq(
            @RequestParam(required = false) String category,
            @RequestParam(required = false) String status,
            @RequestParam(required = false) String keyword,
            @RequestParam(required = false, defaultValue = "hit_count") String sortBy,
            @RequestParam(required = false, defaultValue = "desc") String sortOrder,
            @RequestParam(required = false, defaultValue = "1") int page,
            @RequestParam(required = false, defaultValue = "10") int size) {

        if (!ALLOWED_SORT_COLUMNS.contains(sortBy)) {
            sortBy = "hit_count";
        }
        sortBy = SORT_COLUMN_MAPPING.getOrDefault(sortBy, "hit_count");
        if (!"asc".equalsIgnoreCase(sortOrder) && !"desc".equalsIgnoreCase(sortOrder)) {
            sortOrder = "desc";
        }
        sortOrder = sortOrder.toUpperCase();

        int offset = Math.max(0, (page - 1) * size);
        int limit = Math.max(1, Math.min(size, 100));

        List<FaqEntry> list = faqService.findByFilters(category, status, keyword, sortBy, sortOrder, offset, limit);
        long total = faqService.countByFilters(category, status, keyword);

        return Result.success(new PageResult<>(list, total));
    }

    @GetMapping("/faq/similar")
    public Result<List<Map<String, Object>>> similarFaq(@RequestParam String question) {
        return Result.success(faqService.findSimilar(question));
    }

    @RequireRole("admin")
    @PostMapping("/faq/batch-delete")
    public Result<Map<String, Object>> batchDelete(@RequestBody List<Long> ids) {
        faqService.batchDelete(ids);
        return Result.success(Map.of("success", true, "message", "批量删除完成", "count", ids.size()));
    }

    @RequireRole("admin")
    @PostMapping("/faq/batch-update-category")
    public Result<Map<String, Object>> batchUpdateCategory(@RequestBody Map<String, Object> body) {
        @SuppressWarnings("unchecked")
        List<Long> ids = ((List<Number>) body.get("ids")).stream()
                .map(Number::longValue).collect(java.util.stream.Collectors.toList());
        String category = (String) body.get("category");
        faqService.batchUpdateCategory(ids, category);
        return Result.success(Map.of("success", true, "message", "批量更新分类完成", "count", ids.size()));
    }

    @RequireRole("admin")
    @PostMapping("/faq/batch-update-status")
    public Result<Map<String, Object>> batchUpdateStatus(@RequestBody Map<String, Object> body) {
        @SuppressWarnings("unchecked")
        List<Long> ids = ((List<Number>) body.get("ids")).stream()
                .map(Number::longValue).collect(java.util.stream.Collectors.toList());
        String status = (String) body.get("status");
        faqService.batchUpdateStatus(ids, status);
        return Result.success(Map.of("success", true, "message", "批量更新状态完成", "count", ids.size()));
    }

    @RequireRole("admin")
    @PostMapping("/faq/import")
    public Result<Map<String, Object>> importFaq(@RequestParam("file") MultipartFile file) {
        List<FaqEntry> entries = new ArrayList<>();
        try (java.io.InputStream is = file.getInputStream()) {
            org.apache.poi.ss.usermodel.Workbook workbook =
                org.apache.poi.ss.usermodel.WorkbookFactory.create(is);
            org.apache.poi.ss.usermodel.Sheet sheet = workbook.getSheetAt(0);
            for (int i = 1; i <= sheet.getLastRowNum(); i++) {
                org.apache.poi.ss.usermodel.Row row = sheet.getRow(i);
                if (row == null) continue;
                String question = getCellString(row.getCell(0));
                String answer = getCellString(row.getCell(1));
                if (question == null || question.isBlank()) continue;
                FaqEntry entry = FaqEntry.builder()
                    .question(question)
                    .answer(answer != null ? answer : "")
                    .category(getCellString(row.getCell(2)))
                    .keywords(getCellString(row.getCell(3)))
                    .status("active")
                    .build();
                entries.add(entry);
            }
            workbook.close();
        } catch (Exception e) {
            logger.error("FAQ 导入失败", e);
            return Result.error("文件解析失败: " + e.getMessage());
        }
        int count = 0;
        for (FaqEntry entry : entries) {
            faqService.create(entry);
            count++;
        }
        return Result.success(Map.of("success", true, "message", "导入完成", "count", count));
    }

    private String getCellString(org.apache.poi.ss.usermodel.Cell cell) {
        if (cell == null) return null;
        return switch (cell.getCellType()) {
            case STRING -> cell.getStringCellValue().trim();
            case NUMERIC -> String.valueOf((long) cell.getNumericCellValue());
            default -> null;
        };
    }

    @GetMapping("/faq/export")
    public void exportFaq(
            @RequestParam(required = false) String category,
            @RequestParam(required = false, defaultValue = "csv") String format,
            jakarta.servlet.http.HttpServletResponse response) throws Exception {

        List<FaqEntry> list;
        if (category != null && !category.isBlank()) {
            list = faqService.listByCategory(category);
        } else {
            list = faqService.listAll();
        }

        if ("xlsx".equalsIgnoreCase(format)) {
            response.setContentType("application/vnd.openxmlformats-officedocument.spreadsheetml.sheet");
            response.setHeader("Content-Disposition", "attachment; filename=faq_export.xlsx");
            org.apache.poi.xssf.usermodel.XSSFWorkbook workbook = new org.apache.poi.xssf.usermodel.XSSFWorkbook();
            org.apache.poi.xssf.usermodel.XSSFSheet sheet = workbook.createSheet("FAQ");
            org.apache.poi.xssf.usermodel.XSSFRow header = sheet.createRow(0);
            header.createCell(0).setCellValue("问题");
            header.createCell(1).setCellValue("答案");
            header.createCell(2).setCellValue("分类");
            header.createCell(3).setCellValue("关键词");
            int rowIdx = 1;
            for (FaqEntry f : list) {
                org.apache.poi.xssf.usermodel.XSSFRow row = sheet.createRow(rowIdx++);
                row.createCell(0).setCellValue(f.getQuestion());
                row.createCell(1).setCellValue(f.getAnswer());
                row.createCell(2).setCellValue(f.getCategory() != null ? f.getCategory() : "");
                row.createCell(3).setCellValue(f.getKeywords() != null ? f.getKeywords() : "");
            }
            workbook.write(response.getOutputStream());
            workbook.close();
        } else {
            response.setContentType("text/csv; charset=UTF-8");
            response.setHeader("Content-Disposition", "attachment; filename=faq_export.csv");
            response.getOutputStream().write(new byte[]{(byte) 0xEF, (byte) 0xBB, (byte) 0xBF});
            java.io.PrintWriter writer = response.getWriter();
            writer.println("问题,答案,分类,关键词");
            for (FaqEntry f : list) {
                writer.printf("\"%s\",\"%s\",\"%s\",\"%s\"\n",
                    escapeCsv(f.getQuestion()),
                    escapeCsv(f.getAnswer()),
                    escapeCsv(f.getCategory()),
                    escapeCsv(f.getKeywords()));
            }
            writer.flush();
        }
    }

    private String escapeCsv(String s) {
        if (s == null) return "";
        return s.replace("\"", "\"\"");
    }

    @GetMapping("/faq/high-freq")
    public Result<List<FaqEntry>> highFreqFaq(@RequestParam(defaultValue = "10") int limit) {
        List<FaqEntry> list = faqService.topHighFreq(limit);
        return Result.success(list);
    }

    @RequireRole("admin")
    @GetMapping("/faq/candidates")
    public Result<List<Map<String, Object>>> faqCandidates(
            @RequestParam(defaultValue = "20") int limit,
            @RequestParam(defaultValue = "3") int minFrequency) {
        return Result.success(faqService.getFaqCandidates(limit, minFrequency));
    }

    @GetMapping("/faq/stats")
    public Result<Map<String, Object>> getStats() {
        return Result.success(faqService.getStats());
    }

    @GetMapping("/faq/stats/trend")
    public Result<List<Map<String, Object>>> getDailyTrend(@RequestParam(defaultValue = "30") int days) {
        return Result.success(faqService.getDailyTrend(days));
    }

    @GetMapping("/faq/stats/category-distribution")
    public Result<List<Map<String, Object>>> getCategoryDistribution() {
        return Result.success(faqService.getCategoryDistribution());
    }

}
