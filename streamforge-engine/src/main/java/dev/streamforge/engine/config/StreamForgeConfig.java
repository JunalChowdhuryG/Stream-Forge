package dev.streamforge.engine.config;

import jakarta.validation.Valid;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

/**
 * Configuracion principal del motor StreamForge.
 * Se vincula desde application.yml via @ConfigurationProperties.
 */
@Validated
@ConfigurationProperties(prefix = "streamforge")
public class StreamForgeConfig {

    @NotBlank
    private String instanceId = "streamforge-engine-01";

    @Valid
    private ExecutionConfig execution = new ExecutionConfig();

    @Valid
    private CheckpointConfig checkpoint = new CheckpointConfig();

    @Valid
    private LineageConfig lineage = new LineageConfig();

    @Valid
    private QualityConfig quality = new QualityConfig();

    @Valid
    private SchemaConfig schema = new SchemaConfig();

    public String getInstanceId()               { return instanceId;   }
    public void setInstanceId(String v)         { instanceId = v;      }
    public ExecutionConfig getExecution()       { return execution;    }
    public void setExecution(ExecutionConfig v) { execution = v;       }
    public CheckpointConfig getCheckpoint()     { return checkpoint;   }
    public void setCheckpoint(CheckpointConfig v){ checkpoint = v;     }
    public LineageConfig getLineage()           { return lineage;      }
    public void setLineage(LineageConfig v)     { lineage = v;         }
    public QualityConfig getQuality()           { return quality;      }
    public void setQuality(QualityConfig v)     { quality = v;         }
    public SchemaConfig getSchema()             { return schema;       }
    public void setSchema(SchemaConfig v)       { schema = v;          }

    public static class ExecutionConfig {
        @Min(1) private int maxConcurrentPipelines = 10;
        @Min(1) private int maxConcurrentSteps     = 20;
        @Min(1) private long stepTimeoutSeconds    = 3600;
        @Min(0) private int retryMaxAttempts       = 5;
        @Min(1) private long retryInitialDelayMs   = 1000;
        @Min(1) private long retryMaxDelayMs       = 30000;

        public int getMaxConcurrentPipelines()         { return maxConcurrentPipelines; }
        public void setMaxConcurrentPipelines(int v)   { maxConcurrentPipelines = v;    }
        public int getMaxConcurrentSteps()             { return maxConcurrentSteps;     }
        public void setMaxConcurrentSteps(int v)       { maxConcurrentSteps = v;        }
        public long getStepTimeoutSeconds()            { return stepTimeoutSeconds;     }
        public void setStepTimeoutSeconds(long v)      { stepTimeoutSeconds = v;        }
        public int getRetryMaxAttempts()               { return retryMaxAttempts;       }
        public void setRetryMaxAttempts(int v)         { retryMaxAttempts = v;          }
        public long getRetryInitialDelayMs()           { return retryInitialDelayMs;    }
        public void setRetryInitialDelayMs(long v)     { retryInitialDelayMs = v;       }
        public long getRetryMaxDelayMs()               { return retryMaxDelayMs;        }
        public void setRetryMaxDelayMs(long v)         { retryMaxDelayMs = v;           }
    }

    public static class CheckpointConfig {
        private boolean enabled         = true;
        private boolean checksumEnabled = true;

        public boolean isEnabled()              { return enabled;         }
        public void setEnabled(boolean v)       { enabled = v;            }
        public boolean isChecksumEnabled()      { return checksumEnabled; }
        public void setChecksumEnabled(boolean v){ checksumEnabled = v;  }
    }

    public static class LineageConfig {
        private boolean enabled         = true;
        private String granularity      = "FIELD";
        private int retentionDays       = 90;

        public boolean isEnabled()             { return enabled;       }
        public void setEnabled(boolean v)      { enabled = v;          }
        public String getGranularity()         { return granularity;   }
        public void setGranularity(String v)   { granularity = v;      }
        public int getRetentionDays()          { return retentionDays; }
        public void setRetentionDays(int v)    { retentionDays = v;    }
    }

    public static class QualityConfig {
        private boolean enabled                  = true;
        private boolean failOnThresholdBreached  = true;
        private String reportOutputDir           = "reports/quality";

        public boolean isEnabled()                      { return enabled;                 }
        public void setEnabled(boolean v)               { enabled = v;                    }
        public boolean isFailOnThresholdBreached()      { return failOnThresholdBreached; }
        public void setFailOnThresholdBreached(boolean v){ failOnThresholdBreached = v;  }
        public String getReportOutputDir()              { return reportOutputDir;         }
        public void setReportOutputDir(String v)        { reportOutputDir = v;            }
    }

    public static class SchemaConfig {
        private String driftMode         = "PERMISSIVE";
        private boolean detectNewFields  = true;
        private boolean detectTypeChanges = true;

        public String getDriftMode()               { return driftMode;         }
        public void setDriftMode(String v)         { driftMode = v;            }
        public boolean isDetectNewFields()         { return detectNewFields;   }
        public void setDetectNewFields(boolean v)  { detectNewFields = v;      }
        public boolean isDetectTypeChanges()       { return detectTypeChanges; }
        public void setDetectTypeChanges(boolean v){ detectTypeChanges = v;    }
    }
}