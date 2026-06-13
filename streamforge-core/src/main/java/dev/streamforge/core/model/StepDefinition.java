package dev.streamforge.core.model;

import com.fasterxml.jackson.annotation.JsonProperty;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
* Definicion de un paso individual dentro de un pipeline
* Se deserializa directamente desde el YAML del pipeline
*/
public class StepDefinition {

    private String id;
    private StepType type;
    private String connector;
    private String transform;

    @JsonProperty("depends_on")
    private List<String> dependsOn = new ArrayList<>();

    private Map<String, Object> config  = new HashMap<>();
    private String input;
    private String output;
    private QualityConfig quality;

    public String getId()                          { return id;         }
    public void setId(String v)                    { id = v;            }
    public StepType getType()                      { return type;       }
    public void setType(StepType v)                { type = v;          }
    public String getConnector()                   { return connector;  }
    public void setConnector(String v)             { connector = v;     }
    public String getTransform()                   { return transform;  }
    public void setTransform(String v)             { transform = v;     }
    public List<String> getDependsOn()             { return dependsOn;  }
    public void setDependsOn(List<String> v)       { dependsOn = v;     }
    public Map<String, Object> getConfig()         { return config;     }
    public void setConfig(Map<String, Object> v)   { config = v;        }
    public String getInput()                       { return input;      }
    public void setInput(String v)                 { input = v;         }
    public String getOutput()                      { return output;     }
    public void setOutput(String v)                { output = v;        }
    public QualityConfig getQuality()              { return quality;    }
    public void setQuality(QualityConfig v)        { quality = v;       }

    /**
     * Configuracion de calidad inline dentro de un paso
     */
    public static class QualityConfig {
        private double threshold = 0.95;
        private List<Map<String, Object>> rules = new ArrayList<>();

        public double getThreshold()               { return threshold;  }
        public void setThreshold(double v)         { threshold = v;     }
        public List<Map<String, Object>> getRules(){ return rules;      }
        public void setRules(List<Map<String, Object>> v){ rules = v;   }
    }

    @Override
    public String toString() {
        return "StepDefinition{id='" + id + "', type=" + type + ", dependsOn=" + dependsOn + "}";
    }
}