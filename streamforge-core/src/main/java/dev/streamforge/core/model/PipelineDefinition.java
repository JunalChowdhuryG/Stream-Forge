package dev.streamforge.core.model;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.dataformat.yaml.YAMLFactory;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

/**
 * Definicion completa de un pipeline leida desde YAML.
 * Estructura del YAML:
 *   pipeline:
 *     id: ...
 *     name: ...
 *     steps: [...]
 */
public class PipelineDefinition {

    private String id;
    private String name;
    private String version = "1.0.0";
    private String description;
    private List<StepDefinition> steps = new ArrayList<>();

    public String getId()                          { return id;          }
    public void setId(String v)                    { id = v;             }
    public String getName()                        { return name;        }
    public void setName(String v)                  { name = v;           }
    public String getVersion()                     { return version;     }
    public void setVersion(String v)               { version = v;        }
    public String getDescription()                 { return description; }
    public void setDescription(String v)           { description = v;    }
    public List<StepDefinition> getSteps()         { return steps;       }
    public void setSteps(List<StepDefinition> v)   { steps = v;          }

    //Factory methods
    private static final ObjectMapper YAML_MAPPER = new ObjectMapper(new YAMLFactory())
            .registerModule(new JavaTimeModule());

    /**
     * Carga una definicion de pipeline desde un archivo YAML
     */
    public static PipelineDefinition fromYaml(Path yamlPath) throws IOException {
        return fromYaml(java.nio.file.Files.newInputStream(yamlPath));
    }

    /**
     * Carga una definicion de pipeline desde un InputStream YAML
     */
    public static PipelineDefinition fromYaml(InputStream stream) throws IOException {
        // El YAML tiene la estructura: { pipeline: { id: ..., steps: [...] } }
        var wrapper = YAML_MAPPER.readValue(stream,java.util.Map.class);
        var pipelineMap = wrapper.get("pipeline");
        return YAML_MAPPER.convertValue(pipelineMap, PipelineDefinition.class);
    }

    @Override
    public String toString() {
        return "PipelineDefinition{id='" + id + "', steps=" + steps.size() + "}";
    }
}