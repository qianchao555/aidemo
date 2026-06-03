package com.xiaofuzi.ai.controller;

import com.xiaofuzi.ai.annotation.RequireRole;
import com.xiaofuzi.ai.entity.FaqEntry;
import com.xiaofuzi.ai.service.FaqService;
import com.xiaofuzi.ai.vo.Result;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/faq")
public class FaqController {
    private static final Logger logger = LoggerFactory.getLogger(FaqController.class);
    private final FaqService faqService;


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
    public Result<List<FaqEntry>> listFaq(
            @RequestParam(required = false) String category,
            @RequestParam(required = false) String keyword) {
        List<FaqEntry> list;
        if (keyword != null && !keyword.isBlank()) {
            list = faqService.searchByKeyword(keyword);
        } else if (category != null && !category.isBlank()) {
            list = faqService.listByCategory(category);
        } else {
            list = faqService.listAll();
        }
        return Result.success(list);
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

}
