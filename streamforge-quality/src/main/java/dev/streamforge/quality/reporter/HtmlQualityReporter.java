package dev.streamforge.quality.reporter;

import dev.streamforge.quality.model.FieldQualityResult;
import dev.streamforge.quality.model.QualityReport;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.format.DateTimeFormatter;
import java.util.stream.Collectors;

/**
 * Genera reportes de calidad en formato HTML.
 * El reporte incluye: resumen ejecutivo, tabla por campo y regla,
 * indicador de pass/fail por threshold y timestamp de evaluacion.
 */
public class HtmlQualityReporter {

    private static final Logger log = LoggerFactory.getLogger(HtmlQualityReporter.class);

    private final String outputDir;

    public HtmlQualityReporter(String outputDir) {
        this.outputDir = outputDir;
    }

    public Path generateReport(QualityReport report) throws IOException {
        Path dir = Path.of(outputDir);
        Files.createDirectories(dir);

        String filename = String.format("quality-%s-%s.html",
                report.getDatasetName(),
                report.getEvaluatedAt().toString().replace(":", "-").substring(0, 19));
        Path outputFile = dir.resolve(filename);

        Files.writeString(outputFile, buildHtml(report));
        log.info("Reporte de calidad generado: {}", outputFile);
        return outputFile;
    }

    private String buildHtml(QualityReport report) {
        String statusClass = report.passed() ? "passed" : "failed";
        String statusText  = report.passed() ? "APROBADO" : "FALLIDO";
        String evaluatedAt = report.getEvaluatedAt()
                .atZone(java.time.ZoneId.systemDefault())
                .format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss"));

        String fieldRows = report.getFieldResults().stream()
                .map(r -> buildFieldRow(r, report.getThreshold()))
                .collect(Collectors.joining("\n"));

        return """
            <!DOCTYPE html>
            <html lang="es">
            <head>
              <meta charset="UTF-8">
              <title>Reporte de Calidad - %s</title>
              <style>
                body  { font-family: sans-serif; margin: 40px; color: #1a1a1a; background: #f9f9f9; }
                h1    { color: #1A5276; }
                h2    { color: #1F8A70; margin-top: 32px; }
                .summary { display: grid; grid-template-columns: repeat(4,1fr); gap:16px; margin:24px 0; }
                .card { background: white; border:1px solid #ddd; border-radius:8px;
                        padding:20px; text-align:center; }
                .card .value { font-size:28px; font-weight:500; color:#1A5276; }
                .card .label { font-size:12px; color:#666; margin-top:4px; }
                .status-%s { background: %s; color: white; padding: 8px 20px;
                             border-radius:4px; font-weight:500; display:inline-block; }
                table { width:100%%; border-collapse:collapse; background:white;
                        border-radius:8px; overflow:hidden; box-shadow:0 1px 3px rgba(0,0,0,.1); }
                th    { background:#1A5276; color:white; padding:12px 16px;
                        text-align:left; font-size:13px; }
                td    { padding:10px 16px; border-bottom:1px solid #eee; font-size:13px; }
                tr:last-child td { border-bottom:none; }
                .ok   { color:#1D9E75; font-weight:500; }
                .fail { color:#D85A30; font-weight:500; }
                .ts   { color:#888; font-size:12px; margin-bottom:24px; }
              </style>
            </head>
            <body>
              <h1>Reporte de Calidad de Datos</h1>
              <p class="ts">Dataset: <strong>%s</strong> | Evaluado: %s | Ejecucion: %s</p>
              <span class="status-%s">%s</span>

              <h2>Resumen</h2>
              <div class="summary">
                <div class="card">
                  <div class="value">%,d</div>
                  <div class="label">Registros evaluados</div>
                </div>
                <div class="card">
                  <div class="value">%d</div>
                  <div class="label">Reglas evaluadas</div>
                </div>
                <div class="card">
                  <div class="value">%.1f%%</div>
                  <div class="label">Pass rate global</div>
                </div>
                <div class="card">
                  <div class="value">%.0f%%</div>
                  <div class="label">Threshold minimo</div>
                </div>
              </div>

              <h2>Resultados por campo</h2>
              <table>
                <tr>
                  <th>Campo</th>
                  <th>Regla</th>
                  <th>Registros</th>
                  <th>Aprobaron</th>
                  <th>Fallaron</th>
                  <th>Pass rate</th>
                  <th>Estado</th>
                </tr>
                %s
              </table>
            </body>
            </html>
            """.formatted(
                report.getDatasetName(),
                statusClass,
                report.passed() ? "#1D9E75" : "#D85A30",
                report.getDatasetName(), evaluatedAt, report.getExecutionId(),
                statusClass, statusText,
                report.getTotalRows(),
                report.getFieldResults().size(),
                report.overallPassRate() * 100,
                report.getThreshold() * 100,
                fieldRows
            );
    }

    private String buildFieldRow(FieldQualityResult r, double threshold) {
        boolean ok        = r.meetsThreshold(threshold);
        String stateClass = ok ? "ok" : "fail";
        String stateText  = ok ? "OK" : "FALLO";

        return """
            <tr>
              <td>%s</td>
              <td>%s</td>
              <td>%,d</td>
              <td class="ok">%,d</td>
              <td class="%s">%,d</td>
              <td>%.2f%%</td>
              <td class="%s"><strong>%s</strong></td>
            </tr>
            """.formatted(
                r.getFieldName(), r.getRuleType(),
                r.getTotalRows(), r.getPassed(),
                r.getFailed() > 0 ? "fail" : "ok", r.getFailed(),
                r.getPassRate() * 100,
                stateClass, stateText
            );
    }
}