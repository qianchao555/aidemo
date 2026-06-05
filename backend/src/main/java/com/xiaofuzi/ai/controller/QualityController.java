package com.xiaofuzi.ai.controller;

import com.xiaofuzi.ai.annotation.RequireRole;
import com.xiaofuzi.ai.dto.quality.*;
import com.xiaofuzi.ai.service.QualityService;
import com.xiaofuzi.ai.vo.Result;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/quality")
public class QualityController {

    private static final Logger logger = LoggerFactory.getLogger(QualityController.class);

    private final QualityService qualityService;

    public QualityController(QualityService qualityService) {
        this.qualityService = qualityService;
    }

    @RequireRole("admin")
    @GetMapping("/overview")
    public Result<QualityOverview> getOverview() {
        return Result.success(qualityService.getOverview());
    }

    @RequireRole("admin")
    @GetMapping("/trend")
    public Result<List<DailyRatingTrendItem>> getTrend(@RequestParam(defaultValue = "30") int days) {
        return Result.success(qualityService.getDailyRatingTrend(days));
    }

    @RequireRole("admin")
    @GetMapping("/low-rated")
    public Result<List<LowRatedMessage>> getLowRated(@RequestParam(defaultValue = "20") int limit) {
        return Result.success(qualityService.getLowRatedMessages(limit));
    }

    @RequireRole("admin")
    @GetMapping("/blind-spots")
    public Result<List<BlindSpotItem>> getBlindSpots(@RequestParam(defaultValue = "20") int limit) {
        return Result.success(qualityService.getBlindSpots(limit));
    }

    @RequireRole("admin")
    @GetMapping("/department-stats")
    public Result<List<DepartmentQualityItem>> getDepartmentStats() {
        return Result.success(qualityService.getDepartmentStats());
    }
}
