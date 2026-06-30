package com.training.controller;

import com.training.common.Constants;
import com.training.common.Result;
import com.training.config.SecurityConfig;
import com.training.dto.StatisticsDTO;
import com.training.service.StatisticsService;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.io.*;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/statistics")
@RequiredArgsConstructor
public class StatisticsController {
    
    private final StatisticsService statisticsService;
    
    @Value("${upload.path}")
    private String uploadPath;
    
    @GetMapping("/class/{classId}")
    public Result<StatisticsDTO> getClassStatistics(@PathVariable Long classId,
            @AuthenticationPrincipal SecurityConfig.UserPrincipal principal) {
        Long currentUserId = principal != null ? principal.getUserId() : null;
        Integer currentRole = principal != null ? principal.getRole() : null;
        return statisticsService.getClassStatistics(classId, currentUserId, currentRole);
    }
    
    @GetMapping("/all")
    public Result<List<Map<String, Object>>> getAllClassesStatistics(
            @AuthenticationPrincipal SecurityConfig.UserPrincipal principal) {
        Long currentUserId = principal != null ? principal.getUserId() : null;
        Integer currentRole = principal != null ? principal.getRole() : null;
        return statisticsService.getAllClassesStatistics(currentUserId, currentRole);
    }
    
    /**
     * 【全新】学生多维度能力雷达图 — 根据学生所有已评价报告的AI评分生成五维能力画像
     */
    @GetMapping("/radar")
    public Result<Map<String, Object>> getStudentRadar(
            @AuthenticationPrincipal SecurityConfig.UserPrincipal principal) {
        Long currentUserId = principal != null ? principal.getUserId() : null;
        Integer currentRole = principal != null ? principal.getRole() : null;
        
        if (currentUserId == null) {
            return Result.error("请先登录");
        }
        // 仅学生可查看自己的雷达图
        if (currentRole != null && currentRole != Constants.ROLE_STUDENT) {
            return Result.error("仅学生可查看能力雷达图");
        }
        return statisticsService.getStudentRadar(currentUserId);
    }
    
    @GetMapping("/export/{classId}")
    public void exportScores(@PathVariable Long classId, HttpServletResponse response,
            @AuthenticationPrincipal SecurityConfig.UserPrincipal principal) {
        try {
            Long currentUserId = principal != null ? principal.getUserId() : null;
            Integer currentRole = principal != null ? principal.getRole() : null;
            Result<Map<String, Object>> result = statisticsService.exportScores(classId, uploadPath + "/exports", currentUserId, currentRole);
            
            if (result.getCode() == 200) {
                Map<String, Object> data = result.getData();
                String filename = (String) data.get("filename");
                String fullPath = (String) data.get("path");
                
                File file = new File(fullPath);
                if (!file.exists()) {
                    response.setStatus(404);
                    return;
                }
                
                response.setContentType("application/vnd.openxmlformats-officedocument.spreadsheetml.sheet");
                response.setHeader("Content-Disposition", "attachment; filename=" + 
                        URLEncoder.encode(filename, StandardCharsets.UTF_8));
                
                try (FileInputStream fis = new FileInputStream(file);
                     OutputStream os = response.getOutputStream()) {
                    byte[] buffer = new byte[4096];
                    int bytesRead;
                    while ((bytesRead = fis.read(buffer)) != -1) {
                        os.write(buffer, 0, bytesRead);
                    }
                }
                
                // 删除临时文件
                file.delete();
            } else {
                response.setStatus(500);
                response.getWriter().write(result.getMessage());
            }
        } catch (IOException e) {
            response.setStatus(500);
        }
    }
}
