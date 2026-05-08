package com.example.demo.web;

import com.example.demo.testreports.SurefireReportService;
import com.example.demo.testreports.TestResultRow;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.util.List;
import java.util.Map;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.servlet.support.RequestContextUtils;

@Controller
public class HomeController {

  private final SurefireReportService surefireReportService;

  public HomeController(SurefireReportService surefireReportService) {
    this.surefireReportService = surefireReportService;
  }

  @GetMapping("/")
  public String home(
      Model model, HttpServletRequest request, HttpServletResponse response) {
    response.setHeader("Cache-Control", "no-store, no-cache, must-revalidate");
    response.setHeader("Pragma", "no-cache");
    response.setHeader("Expires", "0");
    Map<String, ?> flashMap = RequestContextUtils.getInputFlashMap(request);
    if (flashMap != null) {
      model.addAllAttributes(flashMap);
    }
    List<TestResultRow> testResults = surefireReportService.loadLatestResults();

    model.addAttribute("testResults", testResults);
    model.addAttribute(
        "reportSources",
        testResults.isEmpty()
            ? surefireReportService.getExistingReportDirectoryPaths()
            : surefireReportService.getResolvedReportDirectoryPaths());
    return "home";
  }
}
